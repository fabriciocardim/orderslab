package com.orderslab.invoice_api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    public static final String PAYMENT_RESERVED = "payment.reserved";
    public static final String INVOICE_ISSUED = "invoice.issued";
    public static final String INVOICE_FAILED = "invoice.failed";
    public static final String PAYMENT_RESERVED_DLT = "payment.reserved.dlt";

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    /** Mesma declaração do payment-api: KafkaAdmin só cria o que falta, e evita criação automática com 1 partição. */
    @Bean
    NewTopic paymentReservedTopic() {
        return topic(PAYMENT_RESERVED);
    }

    /** Dead-letter topic do consumo de payment.reserved (convenção <tópico>.dlt). */
    @Bean
    NewTopic paymentReservedDltTopic() {
        return topic(PAYMENT_RESERVED_DLT);
    }

    @Bean
    NewTopic invoiceIssuedTopic() {
        return topic(INVOICE_ISSUED);
    }

    @Bean
    NewTopic invoiceFailedTopic() {
        return topic(INVOICE_FAILED);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
