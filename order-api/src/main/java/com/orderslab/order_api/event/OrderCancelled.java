package com.orderslab.order_api.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId) implements OrderEvent {

    public static final String TYPE = "OrderCancelled";

    public static OrderCancelled of(UUID orderId, Instant occurredAt) {
        return new OrderCancelled(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId);
    }
}
