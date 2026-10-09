package com.orderslab.payment_api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PaymentEventsContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final UUID orderId = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-10-04T10:15:31.004512Z");

    @Test
    void paymentReservedShouldCarryEnvelopeAndSpecificFields() {
        UUID paymentId = UUID.randomUUID();

        JsonNode json = serialize(PaymentReserved.of(orderId, paymentId, new BigDecimal("10.50"), occurredAt));

        assertEnvelope(json, "PaymentReserved");
        assertThat(json.get("paymentId").asString()).isEqualTo(paymentId.toString());
        assertThat(json.get("amount").isNumber()).isTrue();
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("10.50");
        assertThat(json.size()).isEqualTo(7);
    }

    @Test
    void paymentFailedShouldCarryEnvelopeAndReason() {
        JsonNode json = serialize(PaymentFailed.of(orderId, "AMOUNT_LIMIT_EXCEEDED", occurredAt));

        assertEnvelope(json, "PaymentFailed");
        assertThat(json.get("reason").asString()).isEqualTo("AMOUNT_LIMIT_EXCEEDED");
        assertThat(json.size()).isEqualTo(6);
    }

    @Test
    void inputMessageShouldIgnoreUnknownFields() {
        String json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"orderId\":\"" + orderId
                + "\",\"amount\":10,\"campoNovo\":\"x\",\"customerId\":\"c\"}";

        OrderCreatedMessage message = jsonMapper.readValue(json, OrderCreatedMessage.class);

        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.amount()).isEqualByComparingTo("10");
    }

    private void assertEnvelope(JsonNode json, String expectedType) {
        assertThat(UUID.fromString(json.get("eventId").asString())).isNotNull();
        assertThat(json.get("eventType").asString()).isEqualTo(expectedType);
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-04T10:15:31.004512Z");
        assertThat(json.get("orderId").asString()).isEqualTo(orderId.toString());
    }

    private JsonNode serialize(PaymentEvent event) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(event));
    }
}
