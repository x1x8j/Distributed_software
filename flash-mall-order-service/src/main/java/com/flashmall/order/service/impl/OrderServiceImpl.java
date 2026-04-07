package com.flashmall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.order.entity.Order;
import com.flashmall.order.mapper.OrderMapper;
import com.flashmall.order.mq.PaymentConsumer;
import com.flashmall.order.mq.PaymentMessage;
import com.flashmall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper                   orderMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    @Override
    public Order getOrder(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    @Override
    public List<Order> getOrdersByUser(Long userId) {
        return orderMapper.selectList(
                new QueryWrapper<Order>().eq("user_id", userId).orderByDesc("created_at"));
    }

    /**
     * 模拟支付接口
     *
     * 一致性方案：本方法只做校验 + 发 Kafka 消息，不直接更新 DB。
     * PaymentConsumer 消费消息后，在同一事务中更新订单状态 + 写幂等记录，
     * 保证"支付动作"和"订单状态更新"最终一致。
     *
     * 即使 Kafka 投递失败（at-least-once），消费端幂等表保证不会重复支付。
     */
    @Override
    public void pay(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderException("订单不存在: " + orderId);
        }
        if (!order.getUserId().equals(userId)) {
            throw new OrderException("无权操作该订单");
        }
        if (order.getStatus() != Order.PENDING_PAYMENT) {
            throw new OrderException("订单当前状态不允许支付，status=" + order.getStatus());
        }

        // 发支付消息 → PaymentConsumer 异步更新 status=2
        String messageId = UUID.randomUUID().toString();
        PaymentMessage msg = new PaymentMessage(messageId, orderId, userId, System.currentTimeMillis());
        try {
            String json = objectMapper.writeValueAsString(msg);
            kafkaTemplate.send(PaymentConsumer.PAYMENT_TOPIC, messageId, json);
            log.info("[Pay] 支付消息已投递 orderId={} messageId={}", orderId, messageId);
        } catch (Exception e) {
            throw new OrderException("支付消息发送失败，请重试");
        }
    }

    public static class OrderException extends RuntimeException {
        public OrderException(String msg) { super(msg); }
    }
}
