package com.flashmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flashmall.seckill.entity.SeckillOrder;
import com.flashmall.seckill.entity.SeckillStock;
import com.flashmall.seckill.mapper.SeckillOrderMapper;
import com.flashmall.seckill.mapper.SeckillStockMapper;
import com.flashmall.seckill.mq.OrderMessage;
import com.flashmall.seckill.mq.OrderProducer;
import com.flashmall.seckill.service.SeckillService;
import com.flashmall.seckill.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 秒杀核心服务
 *
 * ① 幂等检查：Redis SETNX  key="seckill:done:{userId}:{productId}"
 *    → 同一用户同一商品只能秒杀一次（Redis 层快速拦截，DB 唯一索引兜底）
 *
 * ② 原子扣库存：Lua 脚本保证 "查询+扣减" 原子性，彻底防止超卖
 *    → 返回 -1=库存未预热  0=售罄  >0=扣减前的库存（成功）
 *
 * ③ 雪花ID：生成全局唯一订单 ID，支持按 userId 分库路由（基因法扩展点）
 *
 * ④ Kafka 削峰：把订单消息异步投递给消费者，接口立即返回 202 Accepted
 *    → 消费者在 DB 层再做一次库存扣减（stock >= qty），防止 Redis 与 DB 不一致导致超卖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    // ── Redis Key 前缀 ────────────────────────────────────────────────────────
    private static final String STOCK_KEY_PREFIX     = "seckill:stock:";
    private static final String IDEMPOTENT_KEY_PREFIX = "seckill:done:";

    // ── Lua 脚本：原子检查并扣减库存 ─────────────────────────────────────────
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

    private final SeckillOrderMapper  orderMapper;
    private final SeckillStockMapper  stockMapper;
    private final RedissonClient      redisson;
    private final SnowflakeIdGenerator snowflake;
    private final OrderProducer        orderProducer;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public long doSeckill(Long userId, Long productId) {
        // ① 幂等检查：SETNX，1 小时内同用户同商品只能成功一次
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + userId + ":" + productId;
        RBucket<String> idempotentBucket = redisson.getBucket(idempotentKey);
        boolean isNew = idempotentBucket.setIfAbsent("1", Duration.ofHours(1));
        if (!isNew) {
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
            idempotentBucket.delete(); // 回滚幂等标记
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

        // ③ 生成雪花订单 ID
        long orderId = snowflake.nextId();

        // ④ 异步发 Kafka 消息（削峰填谷）
        OrderMessage msg = new OrderMessage(orderId, userId, productId, 1, System.currentTimeMillis());
        try {
            orderProducer.send(msg);
        } catch (Exception e) {
            // Kafka 发送失败：回滚 Redis 库存和幂等标记
            redisson.getAtomicLong(stockKey).incrementAndGet();
            idempotentBucket.delete();
            throw new SeckillException("消息队列异常，请稍后重试");
        }

        log.info("[Seckill] orderId={} userId={} productId={} 消息已投递", orderId, userId, productId);
        return orderId;
    }

    // ── 查询 ──────────────────────────────────────────────────────────────────
    @Override
    public SeckillOrder getOrder(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    @Override
    public List<SeckillOrder> getOrdersByUser(Long userId) {
        return orderMapper.selectList(
                new QueryWrapper<SeckillOrder>().eq("user_id", userId)
                                               .orderByDesc("created_at"));
    }

    // ── 库存预热 ──────────────────────────────────────────────────────────────
    @Override
    public void preWarmStock(Long productId) {
        SeckillStock stock = stockMapper.selectById(productId);
        if (stock == null) {
            throw new SeckillException("商品 " + productId + " 的秒杀库存记录不存在");
        }
        String key = STOCK_KEY_PREFIX + productId;
        redisson.getBucket(key, StringCodec.INSTANCE).set(String.valueOf(stock.getStock()));
        log.info("[Stock Warm] productId={} stock={} 已写入 Redis", productId, stock.getStock());
    }

    @Override
    public void preWarmAllStocks() {
        List<SeckillStock> stocks = stockMapper.selectList(null);
        stocks.forEach(s -> {
            String key = STOCK_KEY_PREFIX + s.getProductId();
            redisson.getBucket(key, StringCodec.INSTANCE).set(String.valueOf(s.getStock()));
            log.info("[Stock Warm] productId={} stock={}", s.getProductId(), s.getStock());
        });
        log.info("[Stock Warm] 共预热 {} 个商品库存", stocks.size());
    }

    // ── 内部异常类 ─────────────────────────────────────────────────────────────
    public static class SeckillException extends RuntimeException {
        public SeckillException(String msg) { super(msg); }
    }
}
