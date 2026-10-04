package com.orderslab.invoice_api.config;

import com.orderslab.invoice_api.outbox.OutboxProperties;
import com.orderslab.invoice_api.processing.InvoiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({OutboxProperties.class, InvoiceProperties.class})
public class SchedulingConfig {
}
