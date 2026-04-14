package com.flashmall.seckill.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka 消息体：由 seckill-service 生产，
 * 分别投递到：
 * 1) seckill.orders  → order-service 建单
 * 2) seckill.deduct  → inventory-service 扣库存
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
