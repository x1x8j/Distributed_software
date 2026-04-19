package com.flashmall.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/gateway/fallback")
public class FallbackController {

    @GetMapping("/seckill")
    public ResponseEntity<Map<String, String>> seckillFallback() {
        return ResponseEntity.status(503).body(Map.of("message", "秒杀服务繁忙，请稍后重试"));
    }
}
