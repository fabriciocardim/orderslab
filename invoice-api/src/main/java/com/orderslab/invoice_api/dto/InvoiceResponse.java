package com.orderslab.invoice_api.dto;

import com.orderslab.invoice_api.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class InvoiceResponse {

    private UUID id;
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private InvoiceStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public InvoiceResponse(UUID id, String orderId, String paymentId, BigDecimal amount, InvoiceStatus status,
                            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
