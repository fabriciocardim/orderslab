package com.orderslab.invoice_api.event;

import java.time.Instant;
import java.util.UUID;

public record InvoiceFailed(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId,
        UUID paymentId,
        String reason) implements InvoiceEvent {

    public static final String TYPE = "InvoiceFailed";

    public static InvoiceFailed of(UUID orderId, UUID paymentId, String reason, Instant occurredAt) {
        return new InvoiceFailed(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId, paymentId, reason);
    }
}
