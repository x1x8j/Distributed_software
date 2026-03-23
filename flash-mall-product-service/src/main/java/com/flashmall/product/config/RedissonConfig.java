package com.flashmall.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式创建 RedissonClient，避免 redisson-spring-boot-starter 的自动配置
 * 在 Spring Boot 3.x 中读不到 spring.data.redis.host 的问题。
 * 由于定义了 RedissonClient Bean，自动配置的 @ConditionalOnMissingBean 会跳过创建。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:redis}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}
