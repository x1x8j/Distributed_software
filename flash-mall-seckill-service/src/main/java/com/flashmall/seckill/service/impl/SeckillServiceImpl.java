package com.flashmall.seckill.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flashmall.seckill.config.KafkaTopicConfig;
import com.flashmall.seckill.entity.OutboxMessage;
import com.flashmall.seckill.entity.SeckillStock;
import com.flashmall.seckill.mapper.OutboxMessageMapper;
import com.flashmall.seckill.mapper.SeckillStockMapper;
import com.flashmall.seckill.mq.OrderMessage;
import com.flashmall.seckill.service.SeckillService;
import com.flashmall.seckill.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 秒杀核心服务
 *
 * 职责：快速拦截 + Redis 预扣 + 写 Outbox，不再负责落库和查询。
 * 订单查询 → order-service（/api/orders）
 * 库存扣减 → inventory-service（Kafka seckill.deduct）
 *
 * ① Redis SETNX 幂等检查：同一用户同一商品只能秒杀一次
 * ② Lua 原子扣 Redis 库存：彻底防止超卖
 * ③ 本地消息表（Outbox Pattern）：同一事务写两条消息
 *      seckill.orders  → order-service 建单
 *      seckill.deduct  → inventory-service 扣库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final String STOCK_KEY_PREFIX      = "seckill:stock:";
    private static final String IDEMPOTENT_KEY_PREFIX = "seckill:done:";

    // Lua 脚本：原子检查并扣减库存
    // 返回：-1=key不存在(未预热)  0=库存不足  N(>0)=扣减前的库存(成功)
    private static final String STOCK_DECR_SCRIPT =
        "local key = KEYS[1]\n" +
        "local current = redis.call('GET', key)\n" +
        "if current == false then return -1 end\n" +
        "local stock = tonumber(current)\n" +
        "if stock <= 0 then return 0 end\n" +
        "redis.call('DECR', key)\n" +
        "return stock\n";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SeckillStockMapper   stockMapper;
    private final OutboxMessageMapper  outboxMapper;
    private final RedissonClient       redisson;
    private final SnowflakeIdGenerator snowflake;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long doSeckill(Long userId, Long productId) {
        // ① 幂等检查
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + userId + ":" + productId;
        RBucket<String> idempotentBucket = redisson.getBucket(idempotentKey);
        if (!idempotentBucket.setIfAbsent("1", Duration.ofHours(1))) {
            throw new SeckillException("您已参与过该商品的秒杀");
        }

        // ② 原子扣减 Redis 库存
        String stockKey = STOCK_KEY_PREFIX + productId;
        Long result;
        try {
            result = redisson.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE,
                    STOCK_DECR_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(stockKey));
        } catch (Exception e) {
            idempotentBucket.delete();
            throw new SeckillException("秒杀服务异常，请稍后重试");
        }

        if (result == null || result == -1L) {
            idempotentBucket.delete();
            throw new SeckillException("秒杀活动尚未开始，库存未预热");
        }
        if (result == 0L) {
            idempotentBucket.delete();
            throw new SeckillException("很遗憾，商品已售罄");
        }

        // ③ 基因雪花 ID：低位嵌入 userId%2，保证 id%2=userId%2
        long orderId = snowflake.nextId(userId);

        // ④ 本地消息表 — 同一事务写两条 Outbox，保证消息不丢
        String messageId = UUID.randomUUID().toString();
        OrderMessage msg = new OrderMessage(orderId, userId, productId, 1, System.currentTimeMillis());
        String payload;
        try {
            payload = MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            throw new SeckillException("消息序列化失败");
        }

        // → order-service：创建订单
        outboxMapper.insert(buildOutbox(messageId + ":order",
                KafkaTopicConfig.SECKILL_ORDERS_TOPIC, payload));

        // → inventory-service：扣减独立库存
        outboxMapper.insert(buildOutbox(messageId + ":deduct",
                KafkaTopicConfig.SECKILL_DEDUCT_TOPIC, payload));

        log.info("[Seckill] orderId={} userId={} productId={} 写入2条Outbox消息",
                 orderId, userId, productId);
        return orderId;
    }

    // ── 库存预热 ──────────────────────────────────────────────────────────────
    @Override
    public void preWarmStock(Long productId) {
        SeckillStock stock = stockMapper.selectById(productId);
        if (stock == null) {
            throw new SeckillException("商品 " + productId + " 的秒杀库存记录不存在");
        }
        redisson.getBucket(STOCK_KEY_PREFIX + productId, StringCodec.INSTANCE)
                .set(String.valueOf(stock.getStock()));
        log.info("[Stock Warm] productId={} stock={} 已写入 Redis", productId, stock.getStock());
    }

    @Override
    public void preWarmAllStocks() {
        List<SeckillStock> stocks = stockMapper.selectList(null);
        stocks.forEach(s -> {
            redisson.getBucket(STOCK_KEY_PREFIX + s.getProductId(), StringCodec.INSTANCE)
                    .set(String.valueOf(s.getStock()));
            log.info("[Stock Warm] productId={} stock={}", s.getProductId(), s.getStock());
        });
        log.info("[Stock Warm] 共预热 {} 个商品库存", stocks.size());
    }

    // ── 内部工具 ───────────────────────────────────────────────────────────────
    private OutboxMessage buildOutbox(String messageId, String topic, String payload) {
        OutboxMessage m = new OutboxMessage();
        m.setMessageId(messageId);
        m.setTopic(topic);
        m.setPayload(payload);
        m.setStatus(OutboxMessage.PENDING);
        m.setRetryCount(0);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    public static class SeckillException extends RuntimeException {
        public SeckillException(String msg) { super(msg); }
    }
}
