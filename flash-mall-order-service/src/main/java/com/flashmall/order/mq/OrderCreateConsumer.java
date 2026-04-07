package com.flashmall.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.order.entity.ConsumedMessage;
import com.flashmall.order.entity.Order;
import com.flashmall.order.mapper.ConsumedMessageMapper;
import com.flashmall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消费者1：监听 seckill.orders，创建订单（status=0 待确认库存）
 *
 * 流程：
 *   seckill-service ─[seckill.orders]→ order-service (创建订单, status=0)
 *                                              ↓
 *   seckill-service ─[seckill.deduct]→ inventory-service (扣库存)
 *                                              ↓
 *   inventory-service ─[inventory.result]→ order-service (更新 status=1/3)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    public static final String SECKILL_ORDERS_TOPIC = "seckill.orders";

    private final OrderMapper           orderMapper;
    private final ConsumedMessageMapper consumedMapper;
    private final ObjectMapper          objectMapper;

    @KafkaListener(topics = SECKILL_ORDERS_TOPIC, groupId = "order-service-create")
    @Transactional(rollbackFor = Exception.class)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = record.key();

        // ── 幂等检查 ──────────────────────────────────────────────────────────
        if (consumedMapper.existsByMessageId(messageId) > 0) {
            log.warn("[OrderCreate] 重复消息跳过 messageId={}", messageId);
            ack.acknowledge();
            return;
        }

        SeckillOrderMessage msg;
        try {
            msg = objectMapper.readValue(record.value(), SeckillOrderMessage.class);
        } catch (Exception e) {
            log.error("[OrderCreate] 消息解析失败: {}", record.value(), e);
            ack.acknowledge();
            return;
        }

        log.info("[OrderCreate] 创建订单 orderId={} userId={} productId={}",
                 msg.getOrderId(), msg.getUserId(), msg.getProductId());

        // ── 创建订单（status=0 等待库存确认）──────────────────────────────────
        Order order = new Order();
        order.setId(msg.getOrderId());
        order.setUserId(msg.getUserId());
        order.setProductId(msg.getProductId());
        order.setQuantity(msg.getQuantity());
        order.setAmount(BigDecimal.ZERO);   // 金额可后续由商品服务填充，此处留0
        order.setStatus(Order.PENDING_STOCK);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("[OrderCreate] 订单已存在，幂等跳过 orderId={}", msg.getOrderId());
        }

        // ── 写幂等记录 ────────────────────────────────────────────────────────
        ConsumedMessage consumed = new ConsumedMessage();
        consumed.setMessageId(messageId);
        consumed.setConsumedAt(LocalDateTime.now());
        try {
            consumedMapper.insert(consumed);
        } catch (DuplicateKeyException e) {
            log.warn("[OrderCreate] 幂等记录已存在 messageId={}", messageId);
        }

        ack.acknowledge();
        log.info("[OrderCreate] 订单已创建 orderId={}", msg.getOrderId());
    }
}
