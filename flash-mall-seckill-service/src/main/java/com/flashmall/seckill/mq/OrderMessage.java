package com.flashmall.seckill.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka 消息体：由 seckill 接口生产，由 OrderConsumer 消费写库
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {
    private Long   orderId;
    private Long   userId;
    private Long   productId;
    private int    quantity;
    private long   timestamp;
}
