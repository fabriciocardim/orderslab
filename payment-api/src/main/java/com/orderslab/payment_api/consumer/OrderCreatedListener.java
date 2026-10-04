package com.orderslab.payment_api.consumer;

import com.orderslab.payment_api.config.KafkaTopicsConfig;
import com.orderslab.payment_api.event.OrderCreatedMessage;
import com.orderslab.payment_api.processing.OrderCreatedProcessor;
import java.math.BigDecimal;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);
    private static final int MAX_LOGGED_VALUE = 200;

    private final JsonMapper jsonMapper;
    private final OrderCreatedProcessor processor;

    public OrderCreatedListener(JsonMapper jsonMapper, OrderCreatedProcessor processor) {
        this.jsonMapper = jsonMapper;
        this.processor = processor;
    }

    @KafkaListener(topics = KafkaTopicsConfig.ORDER_CREATED)
    public void onMessage(ConsumerRecord<String, String> record) {
        OrderCreatedMessage message = parse(record);
        if (message == null) {
            return;
        }
        log.atInfo()
                .addKeyValue("orderId", message.orderId())
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("eventType", "OrderCreated")
                .addKeyValue("topic", record.topic())
                .addKeyValue("partition", record.partition())
                .addKeyValue("offset", record.offset())
                .log("OrderCreated received");
        processor.process(message);
    }

    /** Mensagem ilegível ou incompleta é erro permanente: loga e devolve null (descarte), sem travar a partição. */
    private OrderCreatedMessage parse(ConsumerRecord<String, String> record) {
        String value = record.value();
        try {
            OrderCreatedMessage message = jsonMapper.readValue(value, OrderCreatedMessage.class);
            if (message == null || message.eventId() == null || message.orderId() == null
                    || message.amount() == null || message.amount().compareTo(BigDecimal.ZERO) <= 0) {
                discard(record, "missing or invalid required field", null);
                return null;
            }
            return message;
        } catch (JacksonException | IllegalArgumentException e) {
            discard(record, "unreadable JSON", e);
            return null;
        }
    }

    private void discard(ConsumerRecord<String, String> record, String reason, Exception cause) {
        String value = record.value();
        String excerpt = value == null ? null : value.substring(0, Math.min(value.length(), MAX_LOGGED_VALUE));
        var builder = log.atError()
                .addKeyValue("topic", record.topic())
                .addKeyValue("partition", record.partition())
                .addKeyValue("offset", record.offset())
                .addKeyValue("valueExcerpt", excerpt);
        if (cause != null) {
            builder = builder.setCause(cause);
        }
        builder.log("OrderCreated discarded: " + reason);
    }
}
