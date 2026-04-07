package com.flashmall.seckill.mq;

import com.flashmall.seckill.entity.OutboxMessage;
import com.flashmall.seckill.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地消息表轮询器（Outbox Poller）
 *
 * 每隔 2 秒扫描一次 outbox_messages 表中 status=PENDING 的消息，
 * 批量投递到 Kafka。投递成功标记 SENT，失败则递增重试次数，
 * 超过 MAX_RETRY 次后标记 FAILED（需人工介入或补偿任务处理）。
 *
 * 为什么不在 doSeckill 里直接发 Kafka？
 *   → 直接发 Kafka 无法与数据库操作放在同一事务：
 *     若 Kafka 发送后 DB 提交失败，消息已发出无法撤回 → 数据不一致
 *     若 DB 提交后 Kafka 发送失败，消息丢失 → 订单永远停在 status=0
 *   → 本地消息表模式：DB 事务提交即保证消息"意图"持久化，Poller 保证最终投递
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE = 100;

    private final OutboxMessageMapper          outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)   // 每 2 秒轮询一次
    public void poll() {
        List<OutboxMessage> pending = outboxMapper.selectPending(BATCH_SIZE);
        if (pending.isEmpty()) return;

        log.debug("[OutboxPoller] 发现 {} 条待投递消息", pending.size());

        for (OutboxMessage msg : pending) {
            try {
                // 以 messageId 为 Kafka key，保证同一消息落同一分区（顺序+去重辅助）
                kafkaTemplate.send(msg.getTopic(), msg.getMessageId(), msg.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                outboxMapper.markSent(msg.getId());
                                log.debug("[OutboxPoller] 消息投递成功 messageId={}", msg.getMessageId());
                            } else {
                                outboxMapper.incrementRetry(msg.getId(), OutboxMessage.MAX_RETRY);
                                log.warn("[OutboxPoller] 消息投递失败 messageId={}, 已重试 {} 次",
                                         msg.getMessageId(), msg.getRetryCount() + 1, ex);
                            }
                        });
            } catch (Exception e) {
                outboxMapper.incrementRetry(msg.getId(), OutboxMessage.MAX_RETRY);
                log.error("[OutboxPoller] 消息发送异常 messageId={}", msg.getMessageId(), e);
            }
        }
    }
}
