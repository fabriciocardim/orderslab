package com.orderslab.payment_api.config;

import com.orderslab.payment_api.consumer.InvalidMessageException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class KafkaConsumerConfig {

    private static final String DLT_SUFFIX = ".dlt";

    /**
     * Retry limitado com espera exponencial para falha transitória; esgotado, a mensagem vai ao
     * DLT <tópico>.dlt (chave e valor intactos, headers de diagnóstico) e o consumo segue.
     * Conteúdo inválido (InvalidMessageException) vai direto ao DLT, sem retry. Se o envio ao DLT
     * falhar, o registro é reentregue (nunca perdido). A partição -1 deixa o produtor escolher pela
     * chave, mantendo mensagens do mesmo pedido juntas no DLT.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                ConsumerRetryProperties retry, JsonMapper jsonMapper) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff(retry));
        handler.addNotRetryableExceptions(InvalidMessageException.class);
        handler.setRetryListeners(new DltRetryListener(jsonMapper));
        return handler;
    }

    public static ExponentialBackOffWithMaxRetries backOff(ConsumerRetryProperties retry) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialIntervalMs());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxIntervalMs());
        return backOff;
    }
}
