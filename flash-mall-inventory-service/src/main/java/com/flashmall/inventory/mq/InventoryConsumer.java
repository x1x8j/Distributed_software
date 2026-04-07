package com.flashmall.inventory.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.inventory.entity.ConsumedMessage;
import com.flashmall.inventory.mapper.ConsumedMessageMapper;
import com.flashmall.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 库存扣减消费者
 *
 * 监听 seckill.deduct topic，执行真实库存扣减，并将结果发送到 inventory.result topic。
 *
 * 一致性保障：
 * 1. consumed_messages 表幂等检查 —— 防止 Kafka 重复投递导致重复扣库存
 * 2. UPDATE ... WHERE stock >= qty —— DB 层防超卖
 * 3. 扣库存 + 写幂等记录 在同一 @Transactional 原子提交
 * 4. 手动 ACK（MANUAL_IMMEDIATE）—— 处理成功后才提交 offset，保证 at-least-once
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryConsumer {

    static final String DEDUCT_TOPIC = "seckill.deduct";
    static final String RESULT_TOPIC = "inventory.result";

    private final InventoryMapper       inventoryMapper;
    private final ConsumedMessageMapper consumedMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper          objectMapper;

    @KafkaListener(topics = DEDUCT_TOPIC, groupId = "inventory-service")
    @Transactional(rollbackFor = Exception.class)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = record.key();
        String json      = record.value();

        // ── 1. 消费端幂等检查 ────────────────────────────────────────────────
        if (consumedMapper.existsByMessageId(messageId) > 0) {
            log.warn("[Inventory] 重复消息跳过 messageId={}", messageId);
            ack.acknowledge();
            return;
        }

        DeductMessage msg;
        try {
            msg = objectMapper.readValue(json, DeductMessage.class);
        } catch (Exception e) {
            log.error("[Inventory] 消息解析失败，跳过: {}", json, e);
            ack.acknowledge();
            return;
        }

        log.info("[Inventory] 处理扣库存 orderId={} productId={} qty={}",
                 msg.getOrderId(), msg.getProductId(), msg.getQuantity());

        // ── 2. 扣减库存（DB 层防超卖：stock >= qty）────────────────────────
        int rows = inventoryMapper.deductStock(msg.getProductId(), msg.getQuantity());
        boolean success = rows > 0;

        if (!success) {
            log.warn("[Inventory] 库存不足 productId={} qty={}", msg.getProductId(), msg.getQuantity());
        }

        // ── 3. 写消费幂等记录（与扣库存同一事务）────────────────────────────
        ConsumedMessage consumed = new ConsumedMessage();
        consumed.setMessageId(messageId);
        consumed.setConsumedAt(LocalDateTime.now());
        try {
            consumedMapper.insert(consumed);
        } catch (DuplicateKeyException e) {
            // 并发场景下极小概率重复，直接跳过
            log.warn("[Inventory] 并发重复消费 messageId={}", messageId);
            ack.acknowledge();
            return;
        }

        // ── 4. 发送扣库存结果给 order-service ────────────────────────────────
        DeductResult result = new DeductResult(
                msg.getOrderId(), msg.getUserId(), msg.getProductId(),
                msg.getQuantity(), success,
                success ? null : "库存不足");
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            // key 用 orderId，order-service 按 orderId 路由到同一分区
            kafkaTemplate.send(RESULT_TOPIC, String.valueOf(msg.getOrderId()), resultJson);
        } catch (Exception e) {
            log.error("[Inventory] 发送结果消息失败 orderId={}", msg.getOrderId(), e);
            throw new RuntimeException("结果消息发送失败，回滚事务", e);
        }

        ack.acknowledge();
        log.info("[Inventory] 扣库存完成 orderId={} success={}", msg.getOrderId(), success);
    }
}
