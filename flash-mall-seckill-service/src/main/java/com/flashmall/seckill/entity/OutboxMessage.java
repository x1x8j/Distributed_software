package com.flashmall.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表（发件箱 Outbox）
 * 与 seckill_orders 写在同一事务，保证"下单"和"消息投递意图"原子提交。
 * OutboxPoller 扫描 PENDING 状态消息后真正投递 Kafka，实现消息最终一致性。
 */
@Data
@TableName("outbox_messages")
public class OutboxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等 Key，全局唯一，防止重复投递 Kafka */
    private String messageId;

    /** Kafka topic */
    private String topic;

    /** 消息体 JSON */
    private String payload;

    /**
     * 投递状态
     * 0 = PENDING（待投递）
     * 1 = SENT（已投递）
     * 2 = FAILED（重试超限，人工处理）
     */
    private Byte status;

    /** 已重试次数 */
    private Integer retryCount;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    // ── 状态常量 ──────────────────────────────────────────────────────────────
    public static final byte PENDING = 0;
    public static final byte SENT    = 1;
    public static final byte FAILED  = 2;

    /** 最大自动重试次数，超过后标记 FAILED */
    public static final int MAX_RETRY = 5;
}
