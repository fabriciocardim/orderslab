package com.orderslab.payment_api.config;

import com.orderslab.payment_api.outbox.OutboxProperties;
import com.orderslab.payment_api.processing.PaymentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({OutboxProperties.class, PaymentProperties.class})
public class SchedulingConfig {
}
