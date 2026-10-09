package com.orderslab.order_api.outbox;

import com.orderslab.order_api.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava o evento no outbox na mesma transação da transição do pedido. MANDATORY: é impossível
 * enfileirar um evento fora de uma transação.
 */
@Component
public class OutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);
    private static final String TRACE_ID_KEY = "traceId";

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxWriter(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(OrderEvent event, String topic) {
        String payload = jsonMapper.writeValueAsString(event);
        repository.save(new OutboxEvent(
                event.eventId(),
                event.eventType(),
                topic,
                event.orderId(),
                payload,
                MDC.get(TRACE_ID_KEY),
                event.occurredAt()));
        log.atInfo()
                .addKeyValue("orderId", event.orderId())
                .addKeyValue("eventId", event.eventId())
                .addKeyValue("eventType", event.eventType())
                .addKeyValue("topic", topic)
                .log("Event enqueued in outbox");
    }
}
