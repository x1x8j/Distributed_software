package com.flashmall.product.controller;

import com.flashmall.product.entity.Product;
import com.flashmall.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 读：@DS("slave") 走从库 */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getById(id);
        return product == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(product);
    }

    /**
     * 写：@DS("master") 走主库
     * 测试读写分离：POST /api/products   Body: {"name":"Test","price":99.9,"stock":10}
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        return ResponseEntity.ok(productService.create(product));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        String port = System.getenv("SERVER_PORT");
        return ResponseEntity.ok("product-service OK - port:" + (port != null ? port : "unknown"));
    }
}
