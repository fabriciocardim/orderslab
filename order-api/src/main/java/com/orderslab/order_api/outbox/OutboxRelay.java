package com.orderslab.order_api.outbox;

import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica no Kafka os eventos pendentes do outbox, em ordem de id, esperando o ack de cada um, e
 * remove cada linha confirmada. Na primeira falha encerra o lote sem pular linhas (preserva a
 * ordem por pedido) e tenta de novo no próximo ciclo.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}",
            initialDelayString = "${outbox.relay.interval-ms:1000}")
    @Transactional
    public void relayPending() {
        List<OutboxEvent> batch = repository.findBatchForUpdate(PageRequest.of(0, properties.batchSize()));
        for (OutboxEvent event : batch) {
            if (!publish(event)) {
                return;
            }
            repository.delete(event);
        }
    }

    private boolean publish(OutboxEvent event) {
        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                    .get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
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
