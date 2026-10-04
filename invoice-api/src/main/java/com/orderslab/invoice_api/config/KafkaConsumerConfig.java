package com.orderslab.invoice_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    /**
     * O padrão do DefaultErrorHandler tenta 10 vezes sem espera e depois DESCARTA o registro, o que
     * perderia decisões com o banco fora por poucos instantes. Falhas transitórias aqui são
     * repetidas a cada 1 s, sem limite. Mensagens ilegíveis nunca chegam a este handler: o listener
     * as descarta com log. Retry limitado e dead-letter são o item E2.5.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(transientFailureBackOff());
    }

    public static FixedBackOff transientFailureBackOff() {
        return new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS);
    }
}
