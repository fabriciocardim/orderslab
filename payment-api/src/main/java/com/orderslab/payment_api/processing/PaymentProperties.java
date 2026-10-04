package com.orderslab.payment_api.processing;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("payment.approval")
public record PaymentProperties(@DefaultValue("1000.00") BigDecimal limit) {
}
