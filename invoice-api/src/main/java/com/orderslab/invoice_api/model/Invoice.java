package com.orderslab.invoice_api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID sourceEventId;

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

    /** Nota decidida a partir de um evento PaymentReserved (ISSUED ou FAILED). */
    public static Invoice fromEvent(String orderId, String paymentId, BigDecimal amount, UUID sourceEventId,
                                    InvoiceStatus status) {
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.FAILED) {
            throw new IllegalArgumentException("Nota originada de evento só pode nascer ISSUED ou FAILED: " + status);
        }
        Invoice invoice = new Invoice(orderId, paymentId, amount);
        invoice.sourceEventId = sourceEventId;
        invoice.status = status;
        return invoice;
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

    public UUID getSourceEventId() {
        return sourceEventId;
    }
}
