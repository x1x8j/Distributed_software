package com.flashmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.seckill.entity.SeckillOrder;
import com.flashmall.seckill.mapper.SeckillOrderMapper;
import com.flashmall.seckill.mapper.SeckillStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.flashmall.seckill.config.KafkaTopicConfig.SECKILL_ORDERS_TOPIC;

/**
 * Kafka 消费者：异步处理秒杀订单落库
 *
 * 数据一致性保障：
 *   1. INSERT seckill_orders（唯一索引兜底幂等，重复消费直接忽略）
 *   2. UPDATE seckill_stocks SET stock = stock - 1 WHERE stock >= 1（DB 层防超卖）
 *   3. 两步操作在同一事务内，任意失败均回滚
 *
 * 最终状态：
 *   status=1 → 订单成功，DB 库存已扣减
 *   status=2 → DB 库存不足（Redis 与 DB 存在极小差异时触发），订单标记失败
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final SeckillOrderMapper orderMapper;
    private final SeckillStockMapper stockMapper;
    private final ObjectMapper       objectMapper;

    @KafkaListener(topics = SECKILL_ORDERS_TOPIC, groupId = "seckill-order-processor")
    @Transactional
    public void consume(String json) {
        OrderMessage msg;
        try {
            msg = objectMapper.readValue(json, OrderMessage.class);
        } catch (Exception e) {
            log.error("[Consumer] 消息反序列化失败，跳过: {}", json, e);
            return;
        }

        log.info("[Consumer] 处理订单 orderId={} userId={} productId={}",
                 msg.getOrderId(), msg.getUserId(), msg.getProductId());

        // ── 步骤 1：插入订单（status=0 处理中）────────────────────────────────
        SeckillOrder order = new SeckillOrder();
        order.setId(msg.getOrderId());
        order.setUserId(msg.getUserId());
        order.setProductId(msg.getProductId());
        order.setQuantity(msg.getQuantity());
        order.setStatus((byte) 0);
        order.setCreatedAt(LocalDateTime.now());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 → 该用户该商品已有订单，消息幂等处理，直接忽略
            log.warn("[Consumer] 重复消息，忽略 orderId={}", msg.getOrderId());
            return;
        }

        // ── 步骤 2：DB 层扣减库存（防超卖最后防线）───────────────────────────
        int rows = stockMapper.deductStock(msg.getProductId(), msg.getQuantity());
        if (rows == 0) {
            // DB 库存不足（Redis 与 DB 出现极小偏差时触发），订单标记为失败
            orderMapper.updateStatus(msg.getOrderId(), (byte) 2);
            log.warn("[Consumer] DB 库存不足，订单标记失败 orderId={}", msg.getOrderId());
            return;
        }

        // ── 步骤 3：标记订单成功 ──────────────────────────────────────────────
        orderMapper.updateStatus(msg.getOrderId(), (byte) 1);
        log.info("[Consumer] 订单处理成功 orderId={}", msg.getOrderId());
    }
}
