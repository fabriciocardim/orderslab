package com.orderslab.invoice_api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Invoice {

    private UUID id;
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private InvoiceStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public Invoice() {
    }

    public Invoice(String orderId, String paymentId, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = InvoiceStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
