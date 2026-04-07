package com.flashmall.inventory.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * inventory-service 扣库存后发回给 order-service 的结果消息
 * topic: inventory.result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductResult {
    private Long    orderId;
    private Long    userId;
    private Long    productId;
    private Integer quantity;
    private boolean success;    // true=扣减成功，false=库存不足
    private String  reason;     // 失败原因
}
