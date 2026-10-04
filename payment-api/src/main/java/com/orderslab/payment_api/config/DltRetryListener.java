package com.orderslab.payment_api.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.kafka.listener.RetryListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Loga, de forma estruturada, cada retry e cada envio ao DLT (inclusive falha ao enviar). */
public class DltRetryListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(DltRetryListener.class);

    private final JsonMapper jsonMapper;

    public DltRetryListener(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        withContext(log.atWarn(), record)
                .addKeyValue("attempt", deliveryAttempt)
                .setCause(ex)
                .log("Consumption failed; will retry or park in DLT");
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
        withContext(log.atError(), record)
                .addKeyValue("dltTopic", record.topic() + ".dlt")
                .setCause(ex)
                .log("Message sent to DLT");
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        withContext(log.atError(), record)
                .addKeyValue("dltTopic", record.topic() + ".dlt")
                .addKeyValue("originalCause", String.valueOf(original))
                .setCause(failure)
                .log("Could not send message to DLT; it will be redelivered");
    }

    /** orderId/eventId só quando o valor é legível; qualquer falha de parse é ignorada. */
    private LoggingEventBuilder withContext(LoggingEventBuilder base, ConsumerRecord<?, ?> record) {
        LoggingEventBuilder builder = base
                .addKeyValue("topic", record.topic())
                .addKeyValue("partition", record.partition())
                .addKeyValue("offset", record.offset());
        if (record.value() instanceof String value) {
            try {
                JsonNode node = jsonMapper.readTree(value);
                for (String field : new String[] {"orderId", "eventId"}) {
                    JsonNode child = node.path(field);
                    if (child.isString()) {
                        builder = builder.addKeyValue(field, child.asString());
                    }
                }
            } catch (RuntimeException unreadable) {
                log.atDebug()
                        .addKeyValue("topic", record.topic())
                        .addKeyValue("offset", record.offset())
                        .log("Message value is not readable JSON; logging origin only");
            }
        }
        return builder;
    }
}
