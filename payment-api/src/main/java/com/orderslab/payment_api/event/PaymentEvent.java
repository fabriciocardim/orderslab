package com.orderslab.payment_api.event;

import java.time.Instant;
import java.util.UUID;

/** Envelope obrigatório de todo evento publicado pelo payment-api (contrato do E2.1). */
public interface PaymentEvent {

    int CURRENT_VERSION = 1;

    UUID eventId();

    String eventType();

    int eventVersion();

    Instant occurredAt();

    UUID orderId();
}
