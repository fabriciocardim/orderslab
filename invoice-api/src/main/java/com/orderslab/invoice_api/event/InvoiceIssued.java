package com.orderslab.invoice_api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceIssued(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID orderId,
        UUID invoiceId,
        UUID paymentId,
        BigDecimal amount) implements InvoiceEvent {

    public static final String TYPE = "InvoiceIssued";

    public static InvoiceIssued of(UUID orderId, UUID invoiceId, UUID paymentId, BigDecimal amount,
                                   Instant occurredAt) {
        return new InvoiceIssued(UUID.randomUUID(), TYPE, CURRENT_VERSION, occurredAt, orderId, invoiceId,
                paymentId, amount);
    }
}
