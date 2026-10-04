package com.orderslab.invoice_api.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("outbox.relay")
public record OutboxProperties(
        @DefaultValue("1000") long intervalMs,
        @DefaultValue("100") int batchSize,
        @DefaultValue("15000") long sendTimeoutMs) {
}
