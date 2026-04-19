package com.flashmall.order.controller;

import com.flashmall.order.entity.Order;
import com.flashmall.order.service.OrderService;
import com.flashmall.order.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.alibaba.csp.sentinel.annotation.SentinelResource;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 查询单个订单 */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
        Order order = orderService.getOrder(orderId);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }

    /** 查询用户所有订单 */
    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUser(@PathVariable Long userId) {
        return orderService.getOrdersByUser(userId);
    }

    /**
     * 模拟支付接口
     *
     * 一致性方案：
     *   1. 本接口校验订单合法性后发 Kafka 消息（order.payment topic）
     *   2. PaymentConsumer 消费消息，在同一 @Transactional 中：
     *      - 更新订单 status=2（已支付）
     *      - 写 consumed_messages 幂等记录
     *   → 支付动作与状态更新通过消息最终一致，即使服务重启也不会丢失
     *
     * POST /api/orders/{orderId}/pay
     * Header: X-User-Id: {userId}
     */
    @PostMapping("/{orderId}/pay")
    @SentinelResource(value = "orderPayResource", blockHandler = "payBlocked")
    public ResponseEntity<?> pay(@PathVariable Long orderId,
                                 @RequestHeader("X-User-Id") Long userId) {
        try {
            orderService.pay(orderId, userId);
            return ResponseEntity.accepted()
                    .body(Map.of("message", "支付请求已提交，正在处理中", "orderId", orderId));
        } catch (OrderServiceImpl.OrderException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    public ResponseEntity<?> payBlocked(Long orderId, Long userId, com.alibaba.csp.sentinel.slots.block.BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("message", "支付请求过于频繁，请稍后重试", "orderId", orderId));
    }

    /** 健康检查 */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
