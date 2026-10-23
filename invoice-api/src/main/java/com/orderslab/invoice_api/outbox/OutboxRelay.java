package com.orderslab.invoice_api.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica no Kafka os eventos pendentes do outbox, em ordem de id: envia o lote inteiro de uma vez,
 * espera os acks em ordem e remove numa só instrução as linhas confirmadas até a primeira falha
 * (preserva a ordem por pedido; o que veio depois da falha é reenviado no próximo ciclo e os
 * consumidores descartam a duplicata pelo eventId). Lote cheio e sem falha: busca o próximo na hora.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}",
            initialDelayString = "${outbox.relay.interval-ms:1000}")
    public void relayPending() {
        boolean more;
        do {
            more = Boolean.TRUE.equals(transactionTemplate.execute(status -> relayBatch()));
        } while (more);
    }

    /** Uma transação por lote; true quando o lote veio cheio e saiu inteiro (deve haver mais pendentes). */
    private boolean relayBatch() {
        List<OutboxEvent> batch = repository.findBatchForUpdate(PageRequest.of(0, properties.batchSize()));
        List<CompletableFuture<SendResult<String, String>>> sends = batch.stream().map(this::send).toList();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.sendTimeoutMs());
        List<OutboxEvent> published = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            if (!awaitAck(batch.get(i), sends.get(i), deadline)) {
                break;
            }
            published.add(batch.get(i));
        }
        if (!published.isEmpty()) {
            repository.deleteAllInBatch(published);
        }
        return published.size() == properties.batchSize();
    }

    private CompletableFuture<SendResult<String, String>> send(OutboxEvent event) {
        try {
            return kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean awaitAck(OutboxEvent event, CompletableFuture<SendResult<String, String>> send, long deadline) {
        try {
            SendResult<String, String> result = send.get(Math.max(0, deadline - System.nanoTime()),
                    TimeUnit.NANOSECONDS);
            log.atInfo()
                    .addKeyValue("orderId", event.getAggregateId())
                    .addKeyValue("eventId", event.getEventId())
                    .addKeyValue("eventType", event.getEventType())
                    .addKeyValue("topic", event.getTopic())
                    .addKeyValue("partition", result.getRecordMetadata().partition())
                    .addKeyValue("offset", result.getRecordMetadata().offset())
                    .addKeyValue("originTraceId", event.getTraceId())
                    .log("Event published to Kafka");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logFailure(event, e);
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            logFailure(event, e);
            return false;
        }
    }

    private void logFailure(OutboxEvent event, Exception cause) {
        log.atWarn()
                .addKeyValue("orderId", event.getAggregateId())
                .addKeyValue("eventId", event.getEventId())
                .addKeyValue("eventType", event.getEventType())
                .addKeyValue("topic", event.getTopic())
                .addKeyValue("originTraceId", event.getTraceId())
                .setCause(cause)
                .log("Event publication failed; will retry on next cycle");
    }
}
