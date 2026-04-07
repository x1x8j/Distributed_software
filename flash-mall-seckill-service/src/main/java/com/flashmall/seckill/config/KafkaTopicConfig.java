package com.flashmall.seckill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** seckill-service → order-service：建单请求 */
    public static final String SECKILL_ORDERS_TOPIC = "seckill.orders";

    /** seckill-service → inventory-service：扣库存请求 */
    public static final String SECKILL_DEDUCT_TOPIC = "seckill.deduct";

    @Bean
    public NewTopic seckillOrdersTopic() {
        return TopicBuilder.name(SECKILL_ORDERS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic seckillDeductTopic() {
        return TopicBuilder.name(SECKILL_DEDUCT_TOPIC).partitions(3).replicas(1).build();
    }
}
