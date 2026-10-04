package com.orderslab.payment_api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_RESERVED = "payment.reserved";
    public static final String PAYMENT_FAILED = "payment.failed";

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    /** Mesma declaração do order-api: KafkaAdmin só cria o que falta, e evita criação automática com 1 partição. */
    @Bean
    NewTopic orderCreatedTopic() {
        return topic(ORDER_CREATED);
    }

    @Bean
    NewTopic paymentReservedTopic() {
        return topic(PAYMENT_RESERVED);
    }

    @Bean
    NewTopic paymentFailedTopic() {
        return topic(PAYMENT_FAILED);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
