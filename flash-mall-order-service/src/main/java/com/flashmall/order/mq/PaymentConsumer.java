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

import java.time.LocalDateTime;

/**
 * 消费者3：监听 order.payment，处理支付结果 → 更新订单状态
 *
 * /pay 接口发送 order.payment 消息（模拟支付成功/失败）
 * 本消费者消费后更新 status=2（已支付）或 status=4（支付失败）
 *
 * 一致性保障：
 * - 支付场景需幂等（同一笔支付不能重复到账）→ 使用 consumed_messages 表
 * - 更新状态 + 写幂等记录 同一 @Transactional 原子提交
 *
 * 这正是"订单支付 + 订单状态更新一致性"的实现：
 *   /pay 接口只负责发消息（本地事务写 outbox），消费者保证状态最终一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    public static final String PAYMENT_TOPIC = "order.payment";

    private final OrderMapper           orderMapper;
    private final ConsumedMessageMapper consumedMapper;
    private final ObjectMapper          objectMapper;

    @KafkaListener(topics = PAYMENT_TOPIC, groupId = "order-service-payment")
    @Transactional(rollbackFor = Exception.class)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = record.key();

        // ── 幂等检查：同一笔支付消息只处理一次 ──────────────────────────────
        if (consumedMapper.existsByMessageId(messageId) > 0) {
            log.warn("[Payment] 重复支付消息，跳过 messageId={}", messageId);
            ack.acknowledge();
            return;
        }

        PaymentMessage msg;
        try {
            msg = objectMapper.readValue(record.value(), PaymentMessage.class);
        } catch (Exception e) {
            log.error("[Payment] 消息解析失败: {}", record.value(), e);
            ack.acknowledge();
            return;
        }

        log.info("[Payment] 处理支付 orderId={} userId={}", msg.getOrderId(), msg.getUserId());

        // ── 校验订单必须处于"待支付"状态 ─────────────────────────────────────
        Order order = orderMapper.selectById(msg.getOrderId());
        if (order == null) {
            log.warn("[Payment] 订单不存在 orderId={}", msg.getOrderId());
            ack.acknowledge();
            return;
        }
        if (order.getStatus() != Order.PENDING_PAYMENT) {
            log.warn("[Payment] 订单状态不合法，无法支付 orderId={} status={}",
                     msg.getOrderId(), order.getStatus());
            ack.acknowledge();
            return;
        }

        // ── 模拟支付逻辑（实际应调用支付网关）────────────────────────────────
        // 这里始终模拟支付成功；实际场景可调用第三方支付接口
        boolean paySuccess = true;
        byte newStatus = paySuccess ? Order.PAID : Order.PAY_FAILED;

        // ── 更新订单状态 + 写幂等记录（同一事务）────────────────────────────
        orderMapper.updateStatus(msg.getOrderId(), newStatus);

        ConsumedMessage consumed = new ConsumedMessage();
        consumed.setMessageId(messageId);
        consumed.setConsumedAt(LocalDateTime.now());
        try {
            consumedMapper.insert(consumed);
        } catch (DuplicateKeyException e) {
            log.warn("[Payment] 幂等记录并发写入 messageId={}", messageId);
        }

        ack.acknowledge();
        log.info("[Payment] 支付处理完成 orderId={} status={}", msg.getOrderId(), newStatus);
    }
}
