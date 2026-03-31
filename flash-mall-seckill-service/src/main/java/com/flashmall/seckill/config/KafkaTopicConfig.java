package com.flashmall.seckill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String SECKILL_ORDERS_TOPIC = "seckill-orders";

    @Bean
    public NewTopic seckillOrdersTopic() {
        return TopicBuilder.name(SECKILL_ORDERS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
