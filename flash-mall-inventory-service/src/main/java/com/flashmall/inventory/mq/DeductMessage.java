package com.flashmall.inventory.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * seckill-service 发出的扣库存请求消息
 * topic: seckill.deduct
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductMessage {
    private String  messageId;   // outbox.messageId，用于消费端幂等
    private Long    orderId;
    private Long    userId;
    private Long    productId;
    private Integer quantity;
    private Long    timestamp;
}
