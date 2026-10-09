package com.orderslab.invoice_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Política de retry do consumo: retentativas após a 1ª falha, com espera exponencial limitada. */
@ConfigurationProperties("consumer.retry")
public record ConsumerRetryProperties(
        @DefaultValue("4") int maxRetries,
        @DefaultValue("1000") long initialIntervalMs,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("10000") long maxIntervalMs) {
}
