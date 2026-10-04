package com.orderslab.payment_api.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID eventId;
    private String eventType;
    private String topic;
    private UUID aggregateId;
    private String payload;
    private String traceId;
    private Instant occurredAt;
    private Instant createdAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String eventType, String topic, UUID aggregateId,
                       String payload, String traceId, Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
