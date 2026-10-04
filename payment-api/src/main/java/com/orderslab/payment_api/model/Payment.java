package com.orderslab.payment_api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;
    private String orderId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID sourceEventId;

    public Payment() {
    }

    public Payment(String orderId, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.RESERVED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Pagamento decidido a partir de um evento OrderCreated (RESERVED ou FAILED). */
    public static Payment fromEvent(String orderId, BigDecimal amount, UUID sourceEventId, PaymentStatus status) {
        if (status != PaymentStatus.RESERVED && status != PaymentStatus.FAILED) {
            throw new IllegalArgumentException("Pagamento originado de evento só pode nascer RESERVED ou FAILED: " + status);
        }
        Payment payment = new Payment(orderId, amount);
        payment.sourceEventId = sourceEventId;
        payment.status = status;
        return payment;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
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
