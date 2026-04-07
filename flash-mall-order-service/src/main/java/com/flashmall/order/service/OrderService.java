package com.flashmall.order.service;

import com.flashmall.order.entity.Order;

import java.util.List;

public interface OrderService {
    Order getOrder(Long orderId);
    List<Order> getOrdersByUser(Long userId);
    /** 模拟支付：发送支付消息，消费者异步更新状态 */
    void pay(Long orderId, Long userId);
}
