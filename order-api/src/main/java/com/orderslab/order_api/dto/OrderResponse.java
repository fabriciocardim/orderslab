package com.orderslab.order_api.dto;

import com.orderslab.order_api.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderResponse {

    private UUID id;
    private String customerId;
    private BigDecimal amount;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public OrderResponse(UUID id, String customerId, BigDecimal amount, OrderStatus status,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
