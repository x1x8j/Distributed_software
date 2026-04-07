package com.flashmall;

import com.flashmall.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@MapperScan("com.flashmall.seckill.mapper")
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    /**
     * 启动后自动预热所有秒杀商品库存到 Redis。
     * 如果 DB 尚未就绪（首次启动 schema 可能还未执行完），只打 warn，不阻断启动。
     */
    @Bean
    ApplicationRunner stockWarmRunner(SeckillService seckillService) {
        return args -> {
            try {
                seckillService.preWarmAllStocks();
            } catch (Exception e) {
                log.warn("[Startup] 库存预热失败（DB可能尚未就绪），请手动调用 POST /api/seckill/admin/stock/warm/all: {}",
                         e.getMessage());
            }
        };
    }
}
