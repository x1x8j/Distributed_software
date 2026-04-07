package com.flashmall.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * /pay 接口触发的支付请求消息
 * topic: order.payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMessage {
    private String messageId;  // 幂等Key
    private Long   orderId;
    private Long   userId;
    private Long   timestamp;
}
