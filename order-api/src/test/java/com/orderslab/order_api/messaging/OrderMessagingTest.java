package com.orderslab.order_api.messaging;

import static com.orderslab.order_api.messaging.KafkaTestSupport.assertNoMore;
import static com.orderslab.order_api.messaging.KafkaTestSupport.awaitRecords;
import static com.orderslab.order_api.messaging.KafkaTestSupport.headerNames;
import static com.orderslab.order_api.messaging.KafkaTestSupport.partitionCount;
import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.order_api.dto.OrderResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Mensageria do order-api contra Kafka e Postgres reais (E2.6): automatiza a validação manual do E2.2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"outbox.relay.interval-ms=200"})
@AutoConfigureRestTestClient
@Testcontainers
class OrderMessagingTest {

    private static final Duration WAIT = Duration.ofSeconds(45);
    private static final Duration QUIET = Duration.ofSeconds(1);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.0")
            // heap menor que o padrão da imagem (1 GB) e timeout de subida folgado: máquinas/CI pequenos
            .withEnv("KAFKA_HEAP_OPTS", "-Xms128m -Xmx384m")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbc;

    private String bootstrap() {
        return kafka.getBootstrapServers();
    }

    private UUID createOrder(String amount) {
        OrderResponse created = restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cliente-msg\",\"amount\":" + amount + "}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
        return created.getId();
    }

    private void transition(UUID orderId, String action) {
        restTestClient.post().uri("/api/orders/{id}/" + action, orderId)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Espera o evento da chave e devolve uma cópia. A entrega é pelo menos uma vez: cópias idênticas do mesmo
     * evento (reenvio após timeout de ack) são aceitas; dois eventos <i>diferentes</i> para a mesma chave não.
     */
    private ConsumerRecord<String, String> single(String topic, UUID orderId) {
        List<ConsumerRecord<String, String>> records = awaitRecords(bootstrap(), topic, orderId.toString(), 1, WAIT);
        assertThat(KafkaTestSupport.distinctValues(records))
                .as("registros de %s para %s", topic, orderId).isEqualTo(1);
        return records.get(0);
    }

    private static JsonNode assertEnvelope(ConsumerRecord<String, String> record, String eventType, UUID orderId) {
        assertThat(record.key()).as("chave = orderId").isEqualTo(orderId.toString());
        assertThat(headerNames(record)).as("sem header de tipo Java").doesNotContain("__TypeId__");
        JsonNode json = JSON.readTree(record.value());
        assertThat(UUID.fromString(json.get("eventId").asString())).isNotNull();
        assertThat(json.get("eventType").asString()).isEqualTo(eventType);
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(Instant.parse(json.get("occurredAt").asString())).isNotNull();
        assertThat(json.get("orderId").asString()).isEqualTo(orderId.toString());
        return json;
    }

    @Test
    void eachOperationPublishesExactlyOneEventOnTheRightTopic() {
        UUID confirmed = createOrder("10.50");
        transition(confirmed, "confirm");
        UUID cancelled = createOrder("20");
        transition(cancelled, "cancel");

        JsonNode created = assertEnvelope(single("order.created", confirmed), "OrderCreated", confirmed);
        assertThat(created.get("customerId").asString()).isEqualTo("cliente-msg");
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("10.50");
        assertThat(assertEnvelope(single("order.confirmed", confirmed), "OrderConfirmed", confirmed).size())
                .as("OrderConfirmed só tem o envelope").isEqualTo(5);
        assertEnvelope(single("order.created", cancelled), "OrderCreated", cancelled);
        assertThat(assertEnvelope(single("order.cancelled", cancelled), "OrderCancelled", cancelled).size())
                .as("OrderCancelled só tem o envelope").isEqualTo(5);

        assertNoMore(bootstrap(), "order.created", confirmed.toString(), 1, QUIET);
        assertNoMore(bootstrap(), "order.confirmed", confirmed.toString(), 1, QUIET);
        assertNoMore(bootstrap(), "order.cancelled", confirmed.toString(), 0, QUIET);
        assertNoMore(bootstrap(), "order.confirmed", cancelled.toString(), 0, QUIET);
    }

    @Test
    void topicsHaveThreePartitions() {
        assertThat(partitionCount(bootstrap(), "order.created")).isEqualTo(3);
        assertThat(partitionCount(bootstrap(), "order.confirmed")).isEqualTo(3);
        assertThat(partitionCount(bootstrap(), "order.cancelled")).isEqualTo(3);
    }

    @Test
    void createdEventPrecedesConfirmedEventPerOrder() {
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = createOrder("10");
            transition(id, "confirm");
            orders.add(id);
        }

        for (UUID id : orders) {
            long createdAt = single("order.created", id).timestamp();
            long confirmedAt = single("order.confirmed", id).timestamp();
            assertThat(createdAt).as("criação antes da confirmação de %s", id).isLessThanOrEqualTo(confirmedAt);
        }
    }

    @Test
    void brokerDownDoesNotAffectHttpAndEventsArriveAfterRecovery() {
        List<UUID> orders = new ArrayList<>();
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            for (int i = 0; i < 3; i++) {
                orders.add(createOrder("10")); // 201 mesmo com o broker pausado
            }
            transition(orders.get(0), "confirm"); // 200 mesmo com o broker pausado

            assertThat(outboxRowsFor(orders)).as("eventos pendentes no outbox").isEqualTo(4);
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        for (UUID id : orders) {
            assertThat(single("order.created", id).key()).isEqualTo(id.toString());
        }
        single("order.confirmed", orders.get(0));
        for (UUID id : orders) {
            assertNoMore(bootstrap(), "order.created", id.toString(), 1, QUIET);
        }
        assertNoMore(bootstrap(), "order.confirmed", orders.get(0).toString(), 1, QUIET);
        assertThat(outboxRowsFor(orders)).as("outbox esvaziado depois da entrega").isZero();
    }

    private int outboxRowsFor(List<UUID> orders) {
        int total = 0;
        for (UUID id : orders) {
            total += jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?",
                    Integer.class, id);
        }
        return total;
    }
}
