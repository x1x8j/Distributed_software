package com.flashmall.seckill.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill/config")
@RefreshScope
public class DynamicConfigController {

    @Value("${seckill.dynamic-message:seckill-service-default}")
    private String dynamicMessage;

    @GetMapping("/message")
    public Map<String, String> message() {
        return Map.of("message", dynamicMessage);
    }
}
