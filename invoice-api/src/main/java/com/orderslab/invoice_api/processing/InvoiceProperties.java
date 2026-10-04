package com.orderslab.invoice_api.processing;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("invoice.issuance")
public record InvoiceProperties(@DefaultValue("500.00") BigDecimal limit) {
}
