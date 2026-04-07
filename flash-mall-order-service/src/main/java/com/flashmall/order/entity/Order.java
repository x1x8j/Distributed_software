package com.flashmall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long       userId;
    private Long       productId;
    private Integer    quantity;
    private BigDecimal amount;

    /**
     * 订单状态：
     * 0 = 待确认库存（seckill-service 发消息后，inventory-service 尚未回复）
     * 1 = 待支付（inventory 扣减成功）
     * 2 = 已支付
     * 3 = 库存不足已取消
     * 4 = 支付失败
     */
    private Byte status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 状态常量
    public static final byte PENDING_STOCK   = 0;
    public static final byte PENDING_PAYMENT = 1;
    public static final byte PAID            = 2;
    public static final byte CANCELLED       = 3;
    public static final byte PAY_FAILED      = 4;
}
