package com.orderslab.order_api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreated(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId,
        String customerId,
        BigDecimal amount) implements OrderEvent {

    public static final String TYPE = "OrderCreated";

    public static OrderCreated of(UUID orderId, String customerId, BigDecimal amount, Instant occurredAt) {
        return new OrderCreated(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId, customerId, amount);
    }
}
