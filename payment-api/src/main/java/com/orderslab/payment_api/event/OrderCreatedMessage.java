package com.orderslab.payment_api.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem de entrada (OrderCreated, contrato do E2.2). Declarada aqui, sem depender do
 * order-api (Princípio I); campos desconhecidos são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedMessage(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID orderId,
        String customerId,
        BigDecimal amount) {
}
