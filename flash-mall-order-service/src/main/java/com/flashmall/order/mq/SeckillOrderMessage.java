package com.flashmall.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * seckill-service 发出的建单请求
 * topic: seckill.orders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {
    private String  messageId;   // outbox.messageId，消费端幂等用
    private Long    orderId;
    private Long    userId;
    private Long    productId;
    private Integer quantity;
    private Long    timestamp;
}
