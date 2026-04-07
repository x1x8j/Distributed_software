package com.flashmall.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消费幂等表（收件箱 Inbox）
 * 消费者处理完一条消息后插入此表，防止 Kafka 重复投递导致重复扣库存。
 */
@Data
@TableName("consumed_messages")
public class ConsumedMessage {

    @TableId
    private String messageId;

    private LocalDateTime consumedAt;
}
