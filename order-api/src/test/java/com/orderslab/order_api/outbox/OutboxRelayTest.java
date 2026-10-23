package com.orderslab.order_api.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, new OutboxProperties(1000, 100, 1000),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void shouldPublishWithOrderIdKeyAndRemoveAfterAck() {
        OutboxEvent event = event("order.created", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok("order.created"));

        relay.relayPending();

        verify(kafkaTemplate).send("order.created", event.getAggregateId().toString(), event.getPayload());
        verify(repository).deleteAllInBatch(List.of(event));
    }

    @Test
    void shouldPublishInOutboxOrderAcrossOrders() {
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        OutboxEvent aCreated = event("order.created", orderA);
        OutboxEvent bCreated = event("order.created", orderB);
        OutboxEvent aConfirmed = event("order.confirmed", orderA);
        when(repository.findBatchForUpdate(any(Pageable.class)))
                .thenReturn(List.of(aCreated, bCreated, aConfirmed));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok("t"));

        relay.relayPending();

        InOrder order = inOrder(kafkaTemplate);
        order.verify(kafkaTemplate).send("order.created", orderA.toString(), aCreated.getPayload());
        order.verify(kafkaTemplate).send("order.created", orderB.toString(), bCreated.getPayload());
        order.verify(kafkaTemplate).send("order.confirmed", orderA.toString(), aConfirmed.getPayload());
    }

    @Test
    void shouldDeleteOnlyRowsBeforeFirstFailure() {
        OutboxEvent first = event("order.created", UUID.randomUUID());
        OutboxEvent failing = event("order.created", UUID.randomUUID());
        OutboxEvent third = event("order.confirmed", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class))).thenReturn(List.of(first, failing, third));
        when(kafkaTemplate.send(eq("order.created"), eq(first.getAggregateId().toString()), anyString()))
                .thenReturn(ok("order.created"));
        when(kafkaTemplate.send(eq("order.created"), eq(failing.getAggregateId().toString()), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(kafkaTemplate.send(eq("order.confirmed"), eq(third.getAggregateId().toString()), anyString()))
                .thenReturn(ok("order.confirmed"));

        relay.relayPending();

        // o lote sai inteiro antes dos acks: o terceiro foi enviado, mas a linha fica para o próximo ciclo
        verify(repository).deleteAllInBatch(List.of(first));
    }

    @Test
    void shouldKeepRowWhenSendThrowsSynchronously() {
        OutboxEvent event = event("order.created", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("max.block.ms exceeded"));

        relay.relayPending();

        verify(repository, never()).deleteAllInBatch(anyIterable());
    }

    @Test
    void shouldFetchNextBatchRightAwayWhenBatchIsFull() {
        relay = new OutboxRelay(repository, kafkaTemplate, new OutboxProperties(1000, 2, 1000),
                mock(PlatformTransactionManager.class));
        OutboxEvent first = event("order.created", UUID.randomUUID());
        OutboxEvent second = event("order.created", UUID.randomUUID());
        OutboxEvent third = event("order.created", UUID.randomUUID());
        when(repository.findBatchForUpdate(any(Pageable.class)))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(third));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok("order.created"));

        relay.relayPending();

        verify(repository, times(2)).findBatchForUpdate(any(Pageable.class));
        verify(repository).deleteAllInBatch(List.of(first, second));
        verify(repository).deleteAllInBatch(List.of(third));
    }

    private static OutboxEvent event(String topic, UUID orderId) {
        return new OutboxEvent(UUID.randomUUID(), "OrderCreated", topic, orderId,
                "{\"orderId\":\"" + orderId + "\"}", "trace-1", Instant.now());
    }

    private static CompletableFuture<SendResult<String, String>> ok(String topic) {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }
}
