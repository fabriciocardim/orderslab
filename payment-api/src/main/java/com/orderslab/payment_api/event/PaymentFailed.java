package com.orderslab.payment_api.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId,
        String reason) implements PaymentEvent {

    public static final String TYPE = "PaymentFailed";

    public static PaymentFailed of(UUID orderId, String reason, Instant occurredAt) {
        return new PaymentFailed(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId, reason);
    }
}
