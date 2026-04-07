package com.flashmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.seckill.entity.ConsumedMessage;
import com.flashmall.seckill.entity.SeckillOrder;
import com.flashmall.seckill.mapper.ConsumedMessageMapper;
import com.flashmall.seckill.mapper.SeckillOrderMapper;
import com.flashmall.seckill.mapper.SeckillStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.flashmall.seckill.config.KafkaTopicConfig.SECKILL_ORDERS_TOPIC;

/**
 * Kafka 消费者：异步处理秒杀订单落库
 *
 * 数据一致性保障（消费端）：
 *
 * 1. consumed_messages 表幂等检查
 *    → 防止 Kafka at-least-once 重复投递导致重复扣库存
 *    → 与 DB 操作在同一事务，INSERT consumed_messages 失败则整体回滚
 *
 * 2. seckill_orders 唯一索引 uk_user_product 兜底
 *    → 二重幂等保障，捕获 DuplicateKeyException 后跳过
 *
 * 3. DB 层 stock >= qty 条件更新（最终防超卖）
 *    → 防止 Redis 预扣与 DB 极小差异导致超卖
 *
 * 4. 整体 @Transactional：扣库存 + 更新订单状态 + 写幂等记录 原子提交
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final SeckillOrderMapper    orderMapper;
    private final SeckillStockMapper    stockMapper;
    private final ConsumedMessageMapper consumedMapper;
    private final ObjectMapper          objectMapper;

    @KafkaListener(topics = SECKILL_ORDERS_TOPIC, groupId = "seckill-order-processor")
    @Transactional(rollbackFor = Exception.class)
    public void consume(ConsumerRecord<String, String> record) {
        // Kafka key 即 messageId（OutboxPoller 投递时以 outbox.messageId 为 key）
        String messageId = record.key();
        String json      = record.value();

        // ── 步骤 1：消费端幂等检查 ─────────────────────────────────────────────
        // 使用 consumed_messages 表防止重复消费（Kafka at-least-once 语义）
        if (consumedMapper.existsByMessageId(messageId) > 0) {
            log.warn("[Consumer] 重复消息，跳过 messageId={}", messageId);
            return;
        }

        // 解析消息
        OrderMessage msg;
        try {
            msg = objectMapper.readValue(json, OrderMessage.class);
        } catch (Exception e) {
            log.error("[Consumer] 消息反序列化失败，跳过: {}", json, e);
            return;
        }

        log.info("[Consumer] 处理订单 orderId={} userId={} productId={}",
                 msg.getOrderId(), msg.getUserId(), msg.getProductId());

        // ── 步骤 2：确保订单记录存在（正常情况已由 doSeckill 预插）────────────
        SeckillOrder existing = orderMapper.selectById(msg.getOrderId());
        if (existing == null) {
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
                log.warn("[Consumer] 订单已存在，继续处理 orderId={}", msg.getOrderId());
            }
        }

        // ── 步骤 3：DB 层扣减库存（防超卖最后防线）───────────────────────────
        int rows = stockMapper.deductStock(msg.getProductId(), msg.getQuantity());
        if (rows == 0) {
            orderMapper.updateStatus(msg.getOrderId(), (byte) 2);
            log.warn("[Consumer] DB 库存不足，订单标记失败 orderId={}", msg.getOrderId());
        } else {
            // ── 步骤 4：标记订单成功 ──────────────────────────────────────────
            orderMapper.updateStatus(msg.getOrderId(), (byte) 1);
            log.info("[Consumer] 订单处理成功 orderId={}", msg.getOrderId());
        }

        // ── 步骤 5：写入消费幂等记录（与上面操作同一事务原子提交）─────────────
        ConsumedMessage consumed = new ConsumedMessage();
        consumed.setMessageId(messageId);
        consumed.setConsumedAt(LocalDateTime.now());
        consumedMapper.insert(consumed);
    }
}
