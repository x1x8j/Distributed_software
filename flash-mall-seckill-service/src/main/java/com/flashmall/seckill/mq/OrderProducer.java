package com.flashmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.seckill.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送秒杀订单消息到 Kafka。
     * 以 userId 为 key，保证同一用户的消息落在同一分区（有序处理）。
     */
    public void send(OrderMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            kafkaTemplate.send(KafkaTopicConfig.SECKILL_ORDERS_TOPIC,
                               String.valueOf(msg.getUserId()), json);
            log.debug("[MQ] 投递订单消息 orderId={}", msg.getOrderId());
        } catch (Exception e) {
            throw new RuntimeException("Kafka 消息发送失败: " + e.getMessage(), e);
        }
    }
}
