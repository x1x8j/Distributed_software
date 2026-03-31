package com.flashmall.seckill.controller;

import com.flashmall.seckill.entity.SeckillOrder;
import com.flashmall.seckill.service.SeckillService;
import com.flashmall.seckill.service.impl.SeckillServiceImpl.SeckillException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 秒杀下单
     * Header: X-User-Id: {userId}
     * POST /api/seckill/{productId}
     * 返回 202 Accepted + {"orderId": 123456}
     */
    @PostMapping("/{productId}")
    public ResponseEntity<?> doSeckill(
            @PathVariable Long productId,
            @RequestHeader("X-User-Id") Long userId) {
        try {
            long orderId = seckillService.doSeckill(userId, productId);
            return ResponseEntity.accepted()
                                 .body(Map.of("orderId", orderId, "status", "processing"));
        } catch (SeckillException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                                 .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 按订单 ID 查询
     * GET /api/seckill/orders/{orderId}
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
        SeckillOrder order = seckillService.getOrder(orderId);
        return order == null
               ? ResponseEntity.notFound().build()
               : ResponseEntity.ok(order);
    }

    /**
     * 按用户 ID 查询所有秒杀订单
     * GET /api/seckill/orders/user/{userId}
     */
    @GetMapping("/orders/user/{userId}")
    public ResponseEntity<List<SeckillOrder>> getOrdersByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(seckillService.getOrdersByUser(userId));
    }

    /**
     * 预热指定商品库存到 Redis（测试前调用）
     * POST /api/seckill/admin/stock/warm/{productId}
     */
    @PostMapping("/admin/stock/warm/{productId}")
    public ResponseEntity<?> warmStock(@PathVariable Long productId) {
        seckillService.preWarmStock(productId);
        return ResponseEntity.ok(Map.of("message", "productId=" + productId + " 库存预热完成"));
    }

    /**
     * 预热所有商品库存（启动后首先调用）
     * POST /api/seckill/admin/stock/warm/all
     */
    @PostMapping("/admin/stock/warm/all")
    public ResponseEntity<?> warmAllStocks() {
        seckillService.preWarmAllStocks();
        return ResponseEntity.ok(Map.of("message", "所有商品库存预热完成"));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        String port = System.getenv("SERVER_PORT");
        return ResponseEntity.ok("seckill-service OK - port:" + (port != null ? port : "unknown"));
    }
}
