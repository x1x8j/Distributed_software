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

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable("id") Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        String port = System.getenv("SERVER_PORT");
        if (port == null) port = "unknown";
        return ResponseEntity.ok("product-service OK - port:" + port);
    }
}
