package com.orderslab.invoice_api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InvoiceEventsContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-10-04T10:15:32.118004Z");

    @Test
    void invoiceIssuedShouldCarryEnvelopeAndSpecificFields() {
        UUID invoiceId = UUID.randomUUID();

        JsonNode json = serialize(InvoiceIssued.of(orderId, invoiceId, paymentId, new BigDecimal("10.50"), occurredAt));

        assertEnvelope(json, "InvoiceIssued");
        assertThat(json.get("invoiceId").asString()).isEqualTo(invoiceId.toString());
        assertThat(json.get("paymentId").asString()).isEqualTo(paymentId.toString());
        assertThat(json.get("amount").isNumber()).isTrue();
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("10.50");
        assertThat(json.size()).isEqualTo(8);
    }

    @Test
    void invoiceFailedShouldCarryEnvelopePaymentIdAndReason() {
        JsonNode json = serialize(InvoiceFailed.of(orderId, paymentId, "AMOUNT_ABOVE_ISSUANCE_LIMIT", occurredAt));

        assertEnvelope(json, "InvoiceFailed");
        assertThat(json.get("paymentId").asString()).isEqualTo(paymentId.toString());
        assertThat(json.get("reason").asString()).isEqualTo("AMOUNT_ABOVE_ISSUANCE_LIMIT");
        assertThat(json.size()).isEqualTo(7);
    }

    @Test
    void inputMessageShouldIgnoreUnknownFields() {
        String json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"orderId\":\"" + orderId
                + "\",\"paymentId\":\"" + paymentId + "\",\"amount\":10,\"campoNovo\":\"x\"}";

        PaymentReservedMessage message = jsonMapper.readValue(json, PaymentReservedMessage.class);

        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.paymentId()).isEqualTo(paymentId);
        assertThat(message.amount()).isEqualByComparingTo("10");
    }

    private void assertEnvelope(JsonNode json, String expectedType) {
        assertThat(UUID.fromString(json.get("eventId").asString())).isNotNull();
        assertThat(json.get("eventType").asString()).isEqualTo(expectedType);
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-04T10:15:32.118004Z");
        assertThat(json.get("orderId").asString()).isEqualTo(orderId.toString());
    }

    private JsonNode serialize(InvoiceEvent event) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(event));
    }
}
