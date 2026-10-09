package com.orderslab.order_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.model.OrderStatus;
import com.orderslab.order_api.outbox.OutboxRelay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "outbox.relay.interval-ms=3600000",
        "spring.kafka.admin.auto-create=false"})
@AutoConfigureRestTestClient
@Testcontainers
class OrderApiApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboxRelay relay;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void cleanOutboxAndResetKafka() {
        Mockito.reset(kafkaTemplate);
        jdbc.update("delete from outbox_events");
    }

    private static CompletableFuture<SendResult<String, String>> ack() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }

    private static CompletableFuture<SendResult<String, String>> brokerDown() {
        return CompletableFuture.failedFuture(new IllegalStateException("broker down"));
    }

    private UUID createOrder(String customerId) {
        OrderResponse created = restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"" + customerId + "\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
        return created.getId();
    }

    private int outboxRows(UUID orderId) {
        return jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?",
                Integer.class, orderId);
    }

    private List<String> outboxTypes(UUID orderId) {
        return jdbc.queryForList(
                "select event_type from outbox_events where aggregate_id = ? order by id",
                String.class, orderId);
    }

    private int transition(UUID orderId, String action) {
        return restTestClient.post().uri("/api/orders/{id}/" + action, orderId)
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateConfirmAndFindOrderThroughRealHttpFlow() {
        OrderResponse created = restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cliente-1\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);

        restTestClient.post().uri("/api/orders/{id}/confirm", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");

        restTestClient.get().uri("/api/orders/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void shouldCreateAndCancelOrderThroughRealHttpFlow() {
        OrderResponse created = restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cliente-2\",\"amount\":20}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        restTestClient.post().uri("/api/orders/{id}/cancel", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void successfulTransitionsShouldPersistOneOutboxRowEachInOrder() {
        UUID id = createOrder("cliente-outbox");
        assertThat(transition(id, "confirm")).isEqualTo(200);

        assertThat(outboxTypes(id)).containsExactly("OrderCreated", "OrderConfirmed");
    }

    @Test
    void rejectedOperationsShouldNotCreateOutboxRows() {
        UUID unknown = UUID.randomUUID();
        assertThat(transition(unknown, "confirm")).isEqualTo(404);
        assertThat(transition(unknown, "cancel")).isEqualTo(404);
        assertThat(outboxRows(unknown)).isZero();

        UUID id = createOrder("cliente-rejeicao");
        assertThat(transition(id, "cancel")).isEqualTo(200);
        assertThat(transition(id, "confirm")).isEqualTo(409);
        assertThat(outboxTypes(id)).containsExactly("OrderCreated", "OrderCancelled");

        int before = jdbc.queryForObject("select count(*) from outbox_events", Integer.class);
        restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"\",\"amount\":-1}")
                .exchange()
                .expectStatus().isBadRequest();
        int after = jdbc.queryForObject("select count(*) from outbox_events", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void brokerDownShouldNotAffectHttpAndEventsShouldBeDeliveredAfterRecovery() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(brokerDown());

        UUID id = createOrder("cliente-broker-fora");
        assertThat(transition(id, "confirm")).isEqualTo(200);

        relay.relayPending();
        assertThat(outboxTypes(id)).containsExactly("OrderCreated", "OrderConfirmed");

        List<String> delivered = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            delivered.add(inv.getArgument(2));
            return ack();
        });
        relay.relayPending();

        assertThat(outboxRows(id)).isZero();
        assertThat(delivered).hasSize(2);
        assertThat(delivered.get(0)).contains("\"eventType\":\"OrderCreated\"");
        assertThat(delivered.get(1)).contains("\"eventType\":\"OrderConfirmed\"");
    }

    @Test
    void concurrentConfirmAndCancelShouldProduceExactlyOneFinalEvent() throws Exception {
        UUID id = createOrder("cliente-concorrente");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> confirm = pool.submit(() -> {
                start.await();
                return transition(id, "confirm");
            });
            Future<Integer> cancel = pool.submit(() -> {
                start.await();
                return transition(id, "cancel");
            });
            start.countDown();

            assertThat(List.of(confirm.get(), cancel.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }

        List<String> types = outboxTypes(id);
        assertThat(types).hasSize(2);
        assertThat(types.get(0)).isEqualTo("OrderCreated");
        assertThat(types.get(1)).isIn("OrderConfirmed", "OrderCancelled");
    }

    @Test
    void redeliveryShouldReuseTheSameEventId() {
        UUID id = createOrder("cliente-redelivery");
        assertThat(transition(id, "confirm")).isEqualTo(200);

        List<String> attempts = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            attempts.add(inv.getArgument(2));
            return calls.incrementAndGet() == 2 ? brokerDown() : ack();
        });

        relay.relayPending();
        relay.relayPending();

        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(2)).isEqualTo(attempts.get(1));
        assertThat(outboxRows(id)).isZero();
    }

    @Test
    void concurrentRelaysShouldNotLoseOrReorderEventsPerOrder() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID id = createOrder("cliente-relay-" + i);
            assertThat(transition(id, "confirm")).isEqualTo(200);
            ids.add(id);
        }

        List<String[]> sent = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sent.add(new String[] {inv.getArgument(0), inv.getArgument(1)});
            return ack();
        });

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> {
                start.await();
                relay.relayPending();
                return null;
            });
            Future<?> b = pool.submit(() -> {
                start.await();
                relay.relayPending();
                return null;
            });
            start.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(sent).hasSize(40);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
        for (UUID id : ids) {
            List<String> topics = new ArrayList<>();
            synchronized (sent) {
                sent.stream().filter(m -> m[1].equals(id.toString())).forEach(m -> topics.add(m[0]));
            }
            assertThat(topics).containsExactly("order.created", "order.confirmed");
        }
    }
}
