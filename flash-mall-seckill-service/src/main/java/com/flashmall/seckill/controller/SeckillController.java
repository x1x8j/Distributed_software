package com.flashmall.seckill.controller;

import com.flashmall.seckill.service.SeckillService;
import com.flashmall.seckill.service.impl.SeckillServiceImpl.SeckillException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀入口 Controller
 *
 * 职责：接收秒杀请求，Redis 预扣库存，写 Outbox 消息，立即返回 202。
 * 订单查询请访问 order-service：GET /api/orders/{orderId}
 * 库存查询请访问 inventory-service：GET /api/inventory/{productId}
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 秒杀下单
     * POST /api/seckill/{productId}
     * Header: X-User-Id: {userId}
     */
    @PostMapping("/{productId}")
    public ResponseEntity<?> doSeckill(
            @PathVariable Long productId,
            @RequestHeader("X-User-Id") Long userId) {
        try {
            long orderId = seckillService.doSeckill(userId, productId);
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "orderId", orderId,
                            "message", "秒杀成功，订单处理中",
                            "queryUrl", "/api/orders/" + orderId));
        } catch (SeckillException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** 预热指定商品库存 */
    @PostMapping("/admin/stock/warm/{productId}")
    public ResponseEntity<?> warmStock(@PathVariable Long productId) {
        seckillService.preWarmStock(productId);
        return ResponseEntity.ok(Map.of("message", "productId=" + productId + " 库存预热完成"));
    }

    /** 预热所有商品库存（服务启动后调用） */
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
