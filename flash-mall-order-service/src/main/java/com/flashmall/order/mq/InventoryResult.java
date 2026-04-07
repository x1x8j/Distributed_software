package com.flashmall.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * inventory-service 发回的库存扣减结果
 * topic: inventory.result
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResult {
    private Long    orderId;
    private Long    userId;
    private Long    productId;
    private Integer quantity;
    private boolean success;
    private String  reason;
}
