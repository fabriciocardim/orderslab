package com.orderslab.payment_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.event.OrderCreatedMessage;
import com.orderslab.payment_api.event.PaymentEvent;
import com.orderslab.payment_api.model.PaymentStatus;
import com.orderslab.payment_api.outbox.OutboxRelay;
import com.orderslab.payment_api.outbox.OutboxWriter;
import com.orderslab.payment_api.processing.OrderCreatedProcessor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
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
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"})
@AutoConfigureRestTestClient
@Testcontainers
class PaymentApiApplicationTests {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderCreatedProcessor processor;

    @Autowired
    private OutboxRelay relay;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoSpyBean
    private OutboxWriter outboxWriter;

    @BeforeEach
    void resetState() {
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

    private static OrderCreatedMessage message(UUID eventId, UUID orderId, String amount) {
        return new OrderCreatedMessage(eventId, "OrderCreated", 1, Instant.now(), orderId, "cliente",
                new BigDecimal(amount));
    }

    private int paymentsFor(UUID orderId) {
        return jdbc.queryForObject("select count(*) from payments where order_id = ?", Integer.class,
                orderId.toString());
    }

    private int outboxRowsFor(UUID orderId) {
        return jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class,
                orderId);
    }

    private List<String> outboxTypes(UUID orderId) {
        return jdbc.queryForList("select event_type from outbox_events where aggregate_id = ? order by id",
                String.class, orderId);
    }

    private String statusOf(UUID orderId) {
        return jdbc.queryForObject("select status from payments where order_id = ?", String.class,
                orderId.toString());
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReserveConfirmAndFindPaymentThroughRealHttpFlow() {
        PaymentResponse created = restTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.RESERVED);

        restTestClient.post().uri("/api/payments/{id}/confirm", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");

        restTestClient.get().uri("/api/payments/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void shouldReserveAndCancelPaymentThroughRealHttpFlow() {
        PaymentResponse created = restTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"amount\":20}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        restTestClient.post().uri("/api/payments/{id}/cancel", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void lowAmountShouldBeReservedAndPublishedToPaymentReserved() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<String[]> sent = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sent.add(new String[] {inv.getArgument(0), inv.getArgument(1)});
            return ack();
        });

        processor.process(message(eventId, orderId, "10.50"));

        assertThat(statusOf(orderId)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select source_event_id from payments where order_id = ?", UUID.class,
                orderId.toString())).isEqualTo(eventId);
        assertThat(outboxTypes(orderId)).containsExactly("PaymentReserved");

        relay.relayPending();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)[0]).isEqualTo("payment.reserved");
        assertThat(sent.get(0)[1]).isEqualTo(orderId.toString());
        assertThat(outboxRowsFor(orderId)).isZero();
    }

    @Test
    void highAmountShouldFailAndPublishToPaymentFailed() {
        UUID orderId = UUID.randomUUID();
        List<String[]> sent = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sent.add(new String[] {inv.getArgument(0), inv.getArgument(2)});
            return ack();
        });

        processor.process(message(UUID.randomUUID(), orderId, "1500"));

        assertThat(statusOf(orderId)).isEqualTo("FAILED");
        assertThat(outboxTypes(orderId)).containsExactly("PaymentFailed");

        relay.relayPending();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)[0]).isEqualTo("payment.failed");
        assertThat(sent.get(0)[1]).contains("\"reason\":\"AMOUNT_LIMIT_EXCEEDED\"");
    }

    @Test
    void amountExactlyAtTheLimitShouldBeReserved() {
        UUID orderId = UUID.randomUUID();

        processor.process(message(UUID.randomUUID(), orderId, "1000"));

        assertThat(statusOf(orderId)).isEqualTo("RESERVED");
    }

    @Test
    void redeliveredEventShouldNotCreateASecondPaymentOrEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        processor.process(message(eventId, orderId, "10"));
        processor.process(message(eventId, orderId, "10"));
        processor.process(message(eventId, orderId, "10"));

        assertThat(paymentsFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void differentEventIdsForTheSameOrderShouldYieldASinglePayment() {
        UUID orderId = UUID.randomUUID();

        processor.process(message(UUID.randomUUID(), orderId, "10"));
        processor.process(message(UUID.randomUUID(), orderId, "10"));

        assertThat(paymentsFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void failureWritingTheOutboxShouldRollBackThePaymentAndRedeliveryConverges() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        // o spy fica atrás do proxy @Transactional(MANDATORY): stubar pelo alvo, não pelo proxy
        OutboxWriter spiedWriter = AopTestUtils.getUltimateTargetObject(outboxWriter);
        doThrow(new IllegalStateException("outbox down")).when(spiedWriter)
                .enqueue(any(PaymentEvent.class), anyString());

        assertThatThrownBy(() -> processor.process(message(eventId, orderId, "10")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(paymentsFor(orderId)).isZero();
        assertThat(outboxRowsFor(orderId)).isZero();

        Mockito.reset(spiedWriter);
        processor.process(message(eventId, orderId, "10"));

        assertThat(paymentsFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void brokerDownShouldKeepDecisionsAndDeliverEverythingAfterRecovery() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(brokerDown());
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            processor.process(message(UUID.randomUUID(), orderId, i == 1 ? "2000" : "10"));
        }

        relay.relayPending();

        for (UUID orderId : orders) {
            assertThat(paymentsFor(orderId)).isEqualTo(1);
            assertThat(outboxRowsFor(orderId)).isEqualTo(1);
        }

        List<String> deliveredKeys = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            deliveredKeys.add(inv.getArgument(1));
            return ack();
        });
        relay.relayPending();

        assertThat(deliveredKeys).containsExactlyElementsOf(orders.stream().map(UUID::toString).toList());
        orders.forEach(orderId -> assertThat(outboxRowsFor(orderId)).isZero());
    }

    @Test
    void concurrentProcessingOfTheSameEventShouldConvergeToOnePaymentAndOneEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        processor.process(message(eventId, orderId, "10"));
                    } catch (RuntimeException expectedOnConstraintRace) {
                        // o perdedor viola a constraint única; a reentrega converge
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        processor.process(message(eventId, orderId, "10"));

        assertThat(paymentsFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void restContractShouldRemainUnchangedForReservationsAndFailedPayments() {
        UUID orderId = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            restTestClient.post().uri("/api/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"orderId\":\"" + orderId + "\",\"amount\":10}")
                    .exchange()
                    .expectStatus().isCreated();
        }
        assertThat(paymentsFor(orderId)).isEqualTo(2);
        assertThat(outboxRowsFor(orderId)).isZero();

        UUID failedOrder = UUID.randomUUID();
        processor.process(message(UUID.randomUUID(), failedOrder, "5000"));
        UUID failedPaymentId = jdbc.queryForObject("select id from payments where order_id = ?", UUID.class,
                failedOrder.toString());

        restTestClient.post().uri("/api/payments/{id}/confirm", failedPaymentId)
                .exchange()
                .expectStatus().isEqualTo(409);
        restTestClient.get().uri("/api/payments/{id}", failedPaymentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED");
    }

    @Test
    void redeliveryAfterFailureShouldReuseTheSameEventId() {
        UUID orderId = UUID.randomUUID();
        processor.process(message(UUID.randomUUID(), orderId, "10"));
        String storedPayload = jdbc.queryForObject("select payload from outbox_events where aggregate_id = ?",
                String.class, orderId);
        List<String> attempts = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            attempts.add(inv.getArgument(2));
            return attempts.size() == 1 ? brokerDown() : ack();
        });

        relay.relayPending();
        relay.relayPending();

        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0)).isEqualTo(storedPayload);
        assertThat(attempts.get(1)).isEqualTo(storedPayload);
        assertThat(outboxRowsFor(orderId)).isZero();
    }

    @Test
    void concurrentRelaysShouldNotLoseOrDuplicateEvents() throws Exception {
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            processor.process(message(UUID.randomUUID(), orderId, "10"));
        }
        List<String> sentKeys = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sentKeys.add(inv.getArgument(1));
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

        assertThat(sentKeys).hasSize(20);
        assertThat(sentKeys).containsExactlyInAnyOrderElementsOf(orders.stream().map(UUID::toString).toList());
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
    }
}
