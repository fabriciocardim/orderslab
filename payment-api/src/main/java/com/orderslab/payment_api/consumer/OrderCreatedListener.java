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

    private final JsonMapper jsonMapper;
    private final OrderCreatedProcessor processor;

    public OrderCreatedListener(JsonMapper jsonMapper, OrderCreatedProcessor processor) {
        this.jsonMapper = jsonMapper;
        this.processor = processor;
    }

    @KafkaListener(topics = KafkaTopicsConfig.ORDER_CREATED)
    public void onMessage(ConsumerRecord<String, String> record) {
        OrderCreatedMessage message = parse(record);
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

    /** Conteúdo inválido é falha permanente: lança InvalidMessageException (vai direto ao DLT, sem retry). */
    private OrderCreatedMessage parse(ConsumerRecord<String, String> record) {
        try {
            OrderCreatedMessage message = jsonMapper.readValue(record.value(), OrderCreatedMessage.class);
            if (message == null || message.eventId() == null || message.orderId() == null
                    || message.amount() == null || message.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidMessageException("missing or invalid required field");
            }
            return message;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidMessageException("unreadable JSON", e);
        }
    }
}
