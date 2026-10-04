package com.orderslab.order_api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OrderEventsContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final UUID orderId = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-10-04T10:15:30.123456Z");

    @Test
    void orderCreatedShouldCarryEnvelopeAndSpecificFields() {
        JsonNode json = serialize(OrderCreated.of(orderId, "cliente-1", new BigDecimal("10.50"), occurredAt));

        assertEnvelope(json, "OrderCreated");
        assertThat(json.get("customerId").asString()).isEqualTo("cliente-1");
        assertThat(json.get("amount").isNumber()).isTrue();
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("10.50");
        assertThat(json.size()).isEqualTo(7);
    }

    @Test
    void orderConfirmedShouldCarryOnlyEnvelope() {
        JsonNode json = serialize(OrderConfirmed.of(orderId, occurredAt));

        assertEnvelope(json, "OrderConfirmed");
        assertThat(json.size()).isEqualTo(5);
    }

    @Test
    void orderCancelledShouldCarryOnlyEnvelope() {
        JsonNode json = serialize(OrderCancelled.of(orderId, occurredAt));

        assertEnvelope(json, "OrderCancelled");
        assertThat(json.size()).isEqualTo(5);
    }

    @Test
    void eventIdShouldBeUniquePerEvent() {
        assertThat(OrderConfirmed.of(orderId, occurredAt).eventId())
                .isNotEqualTo(OrderConfirmed.of(orderId, occurredAt).eventId());
    }

    private void assertEnvelope(JsonNode json, String expectedType) {
        assertThat(UUID.fromString(json.get("eventId").asString())).isNotNull();
        assertThat(json.get("eventType").asString()).isEqualTo(expectedType);
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-04T10:15:30.123456Z");
        assertThat(json.get("orderId").asString()).isEqualTo(orderId.toString());
    }

    private JsonNode serialize(OrderEvent event) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(event));
    }
}
