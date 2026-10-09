package com.orderslab.invoice_api.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem de entrada (PaymentReserved, contrato do E2.3). Declarada aqui, sem depender do
 * payment-api (Princípio I); campos desconhecidos são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentReservedMessage(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount) {
}
