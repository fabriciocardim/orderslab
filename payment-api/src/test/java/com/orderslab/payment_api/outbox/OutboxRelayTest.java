package com.orderslab.payment_api.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, new OutboxProperties(1000, 100, 1000));
    }

    @Test
    void shouldPublishWithOrderIdKeyAndRemoveAfterAck() {
        OutboxEvent event = event("payment.reserved", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok("payment.reserved"));

        relay.relayPending();

        verify(kafkaTemplate).send("payment.reserved", event.getAggregateId().toString(), event.getPayload());
        verify(repository).delete(event);
    }

    @Test
    void shouldPublishInOutboxOrderAcrossOrders() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        OutboxEvent aCreated = event("payment.reserved", orderA);
        OutboxEvent bCreated = event("payment.reserved", orderB);
        OutboxEvent aConfirmed = event("payment.failed", orderA);
        when(repository.findBatchForUpdate(any(Pageable.class)))
                .thenReturn(List.of(aCreated, bCreated, aConfirmed));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok("t"));

        relay.relayPending();

        InOrder order = inOrder(kafkaTemplate);
        order.verify(kafkaTemplate).send("payment.reserved", orderA.toString(), aCreated.getPayload());
        order.verify(kafkaTemplate).send("payment.reserved", orderB.toString(), bCreated.getPayload());
        order.verify(kafkaTemplate).send("payment.failed", orderA.toString(), aConfirmed.getPayload());
    }

    @Test
    void shouldStopBatchOnFirstFailureWithoutSkippingRows() {
        OutboxEvent first = event("payment.reserved", UUID.randomUUID());
        OutboxEvent failing = event("payment.reserved", UUID.randomUUID());
        OutboxEvent third = event("payment.failed", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class))).thenReturn(List.of(first, failing, third));
        when(kafkaTemplate.send(eq("payment.reserved"), eq(first.getAggregateId().toString()), anyString()))
                .thenReturn(ok("payment.reserved"));
        when(kafkaTemplate.send(eq("payment.reserved"), eq(failing.getAggregateId().toString()), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.relayPending();

        verify(repository).delete(first);
        verify(repository, never()).delete(failing);
        verify(repository, never()).delete(third);
        verify(kafkaTemplate, never()).send(eq("payment.failed"), anyString(), anyString());
    }

    @Test
    void shouldKeepRowWhenSendThrowsSynchronously() {
        OutboxEvent event = event("payment.reserved", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("max.block.ms exceeded"));

        relay.relayPending();

        verify(repository, never()).delete(any(OutboxEvent.class));
    }

    private static OutboxEvent event(String topic, UUID orderId) {
        return new OutboxEvent(UUID.randomUUID(), "PaymentReserved", topic, orderId,
                "{\"orderId\":\"" + orderId + "\"}", "trace-1", Instant.now());
    }

    private static CompletableFuture<SendResult<String, String>> ok(String topic) {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }
}
