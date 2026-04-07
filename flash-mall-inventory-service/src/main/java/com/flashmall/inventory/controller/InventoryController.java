package com.flashmall.inventory.controller;

import com.flashmall.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 库存查询接口（供运维/前端查看，扣减由 Kafka 消费者异步完成）
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryMapper inventoryMapper;

    /** 查询指定商品库存 */
    @GetMapping("/{productId}")
    public ResponseEntity<?> getStock(@PathVariable Long productId) {
        Integer stock = inventoryMapper.selectStock(productId);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("productId", productId, "stock", stock));
    }

    /** 健康检查 */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
