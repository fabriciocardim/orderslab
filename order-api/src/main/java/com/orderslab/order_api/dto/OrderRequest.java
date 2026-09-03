package com.orderslab.order_api.dto;

import java.math.BigDecimal;

public class OrderRequest {

    private String customerId;
    private BigDecimal amount;

    public OrderRequest() {
    }

    public OrderRequest(String customerId, BigDecimal amount) {
        this.customerId = customerId;
        this.amount = amount;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
