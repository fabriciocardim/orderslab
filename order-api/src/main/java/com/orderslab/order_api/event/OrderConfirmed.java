package com.orderslab.order_api.event;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmed(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId) implements OrderEvent {

    public static final String TYPE = "OrderConfirmed";

    public static OrderConfirmed of(UUID orderId, Instant occurredAt) {
        return new OrderConfirmed(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId);
    }
}
