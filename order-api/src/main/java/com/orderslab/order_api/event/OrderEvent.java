package com.orderslab.order_api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope obrigatório de todo evento de pedido (contrato do E2.1). Os campos específicos de
 * cada tipo ficam nos records que implementam esta interface.
 */
public interface OrderEvent {

    int CURRENT_VERSION = 1;

    UUID eventId();

    String eventType();

    int eventVersion();

    Instant occurredAt();

    UUID orderId();
}
