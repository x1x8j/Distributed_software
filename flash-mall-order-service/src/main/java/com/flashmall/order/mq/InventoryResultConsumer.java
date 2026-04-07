package com.flashmall.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.order.entity.Order;
import com.flashmall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费者2：监听 inventory.result，根据库存扣减结果更新订单状态
 *
 * 成功 → status=1（待支付）
 * 失败 → status=3（库存不足已取消）
 *
 * 一致性保障：
 * - updateStatus 本身是幂等的（SET status=x WHERE id=?），重复消费结果相同，无需 consumed_messages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryResultConsumer {

    public static final String INVENTORY_RESULT_TOPIC = "inventory.result";

    private final OrderMapper  orderMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = INVENTORY_RESULT_TOPIC, groupId = "order-service-inventory")
    @Transactional(rollbackFor = Exception.class)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        InventoryResult result;
        try {
            result = objectMapper.readValue(record.value(), InventoryResult.class);
        } catch (Exception e) {
            log.error("[InventoryResult] 消息解析失败: {}", record.value(), e);
            ack.acknowledge();
            return;
        }

        byte newStatus = result.isSuccess() ? Order.PENDING_PAYMENT : Order.CANCELLED;
        orderMapper.updateStatus(result.getOrderId(), newStatus);

        ack.acknowledge();
        log.info("[InventoryResult] 订单状态更新 orderId={} status={} success={}",
                 result.getOrderId(), newStatus, result.isSuccess());
    }
}
