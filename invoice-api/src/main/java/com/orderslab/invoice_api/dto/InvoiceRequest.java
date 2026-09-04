package com.orderslab.invoice_api.dto;

import java.math.BigDecimal;

public class InvoiceRequest {

    private String orderId;
    private String paymentId;
    private BigDecimal amount;

    public InvoiceRequest() {
    }

    public InvoiceRequest(String orderId, String paymentId, BigDecimal amount) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
