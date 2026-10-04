package com.orderslab.payment_api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReserved(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount) implements PaymentEvent {

    public static final String TYPE = "PaymentReserved";

    public static PaymentReserved of(UUID orderId, UUID paymentId, BigDecimal amount, Instant occurredAt) {
        return new PaymentReserved(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId, paymentId, amount);
    }
}
