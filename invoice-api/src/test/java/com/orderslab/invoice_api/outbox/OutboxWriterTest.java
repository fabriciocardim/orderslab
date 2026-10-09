package com.orderslab.invoice_api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.orderslab.invoice_api.event.InvoiceFailed;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxEventRepository repository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPersistSerializedEventWithRoutingData() {
        UUID orderId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-04T10:00:00Z");
        InvoiceFailed event = InvoiceFailed.of(orderId, UUID.randomUUID(), "AMOUNT_ABOVE_ISSUANCE_LIMIT", occurredAt);
        MDC.put("traceId", "abc123");

        new OutboxWriter(repository, jsonMapper).enqueue(event, "invoice.failed");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getEventType()).isEqualTo("InvoiceFailed");
        assertThat(saved.getTopic()).isEqualTo("invoice.failed");
        assertThat(saved.getAggregateId()).isEqualTo(orderId);
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getTraceId()).isEqualTo("abc123");
        assertThat(saved.getPayload()).contains(event.eventId().toString(), "\"eventType\":\"InvoiceFailed\"");
    }

    @Test
    void shouldAllowMissingTraceId() {
        new OutboxWriter(repository, jsonMapper)
                .enqueue(InvoiceFailed.of(UUID.randomUUID(), UUID.randomUUID(), "AMOUNT_ABOVE_ISSUANCE_LIMIT", Instant.now()), "invoice.failed");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTraceId()).isNull();
    }
}
