package com.orderslab.invoice_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.model.InvoiceStatus;
import com.orderslab.invoice_api.event.InvoiceEvent;
import com.orderslab.invoice_api.event.PaymentReservedMessage;
import com.orderslab.invoice_api.outbox.OutboxRelay;
import com.orderslab.invoice_api.outbox.OutboxWriter;
import com.orderslab.invoice_api.processing.PaymentReservedProcessor;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
class InvoiceApiApplicationTests {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String PAYMENT_ID = "a8541a91-1fcc-4b2e-9fdc-aa23f090043a";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentReservedProcessor processor;

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

    private static PaymentReservedMessage message(UUID eventId, UUID orderId, String amount) {
        return new PaymentReservedMessage(eventId, "PaymentReserved", 1, Instant.now(), orderId, UUID.randomUUID(),
                new BigDecimal(amount));
    }

    private int invoicesFor(UUID orderId) {
        return jdbc.queryForObject("select count(*) from invoices where order_id = ?", Integer.class,
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
        return jdbc.queryForObject("select status from invoices where order_id = ?", String.class,
                orderId.toString());
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateIssueAndFindInvoiceThroughRealHttpFlow() {
        InvoiceResponse created = restTestClient.post().uri("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"paymentId\":\"" + PAYMENT_ID + "\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InvoiceResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(InvoiceStatus.PENDING);

        restTestClient.post().uri("/api/invoices/{id}/issue", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ISSUED");

        restTestClient.get().uri("/api/invoices/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ISSUED");
    }

    @Test
    void shouldCreateAndCancelInvoiceThroughRealHttpFlow() {
        InvoiceResponse created = restTestClient.post().uri("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"paymentId\":\"" + PAYMENT_ID + "\",\"amount\":20}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InvoiceResponse.class)
                .returnResult()
                .getResponseBody();

        restTestClient.post().uri("/api/invoices/{id}/cancel", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void lowAmountShouldBeIssuedAndPublishedToInvoiceIssued() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<String[]> sent = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sent.add(new String[] {inv.getArgument(0), inv.getArgument(1)});
            return ack();
        });

        processor.process(message(eventId, orderId, "10.50"));

        assertThat(statusOf(orderId)).isEqualTo("ISSUED");
        assertThat(jdbc.queryForObject("select source_event_id from invoices where order_id = ?", UUID.class,
                orderId.toString())).isEqualTo(eventId);
        assertThat(outboxTypes(orderId)).containsExactly("InvoiceIssued");

        relay.relayPending();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)[0]).isEqualTo("invoice.issued");
        assertThat(sent.get(0)[1]).isEqualTo(orderId.toString());
        assertThat(outboxRowsFor(orderId)).isZero();
    }

    @Test
    void highAmountShouldFailAndPublishToInvoiceFailed() {
        UUID orderId = UUID.randomUUID();
        List<String[]> sent = Collections.synchronizedList(new ArrayList<>());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            sent.add(new String[] {inv.getArgument(0), inv.getArgument(2)});
            return ack();
        });

        processor.process(message(UUID.randomUUID(), orderId, "750"));

        assertThat(statusOf(orderId)).isEqualTo("FAILED");
        assertThat(outboxTypes(orderId)).containsExactly("InvoiceFailed");

        relay.relayPending();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)[0]).isEqualTo("invoice.failed");
        assertThat(sent.get(0)[1]).contains("\"reason\":\"AMOUNT_ABOVE_ISSUANCE_LIMIT\"");
    }

    @Test
    void amountExactlyAtTheLimitShouldBeIssued() {
        UUID orderId = UUID.randomUUID();

        processor.process(message(UUID.randomUUID(), orderId, "500"));

        assertThat(statusOf(orderId)).isEqualTo("ISSUED");
    }

    @Test
    void redeliveredEventShouldNotCreateASecondPaymentOrEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        processor.process(message(eventId, orderId, "10"));
        processor.process(message(eventId, orderId, "10"));
        processor.process(message(eventId, orderId, "10"));

        assertThat(invoicesFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void differentEventIdsForTheSameOrderShouldYieldASinglePayment() {
        UUID orderId = UUID.randomUUID();

        processor.process(message(UUID.randomUUID(), orderId, "10"));
        processor.process(message(UUID.randomUUID(), orderId, "10"));

        assertThat(invoicesFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void failureWritingTheOutboxShouldRollBackThePaymentAndRedeliveryConverges() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        // o spy fica atrás do proxy @Transactional(MANDATORY): stubar pelo alvo, não pelo proxy
        OutboxWriter spiedWriter = AopTestUtils.getUltimateTargetObject(outboxWriter);
        doThrow(new IllegalStateException("outbox down")).when(spiedWriter)
                .enqueue(any(InvoiceEvent.class), anyString());

        assertThatThrownBy(() -> processor.process(message(eventId, orderId, "10")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(invoicesFor(orderId)).isZero();
        assertThat(outboxRowsFor(orderId)).isZero();

        Mockito.reset(spiedWriter);
        processor.process(message(eventId, orderId, "10"));

        assertThat(invoicesFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void brokerDownShouldKeepDecisionsAndDeliverEverythingAfterRecovery() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(brokerDown());
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            processor.process(message(UUID.randomUUID(), orderId, i == 1 ? "900" : "10"));
        }

        relay.relayPending();

        for (UUID orderId : orders) {
            assertThat(invoicesFor(orderId)).isEqualTo(1);
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

        assertThat(invoicesFor(orderId)).isEqualTo(1);
        assertThat(outboxRowsFor(orderId)).isEqualTo(1);
    }

    @Test
    void restContractShouldRemainUnchangedForManualInvoicesAndFailedOnes() {
        UUID orderId = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            restTestClient.post().uri("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"orderId\":\"" + orderId + "\",\"paymentId\":\"" + UUID.randomUUID()
                            + "\",\"amount\":10}")
                    .exchange()
                    .expectStatus().isCreated();
        }
        assertThat(invoicesFor(orderId)).isEqualTo(2);
        assertThat(outboxRowsFor(orderId)).isZero();

        UUID failedOrder = UUID.randomUUID();
        processor.process(message(UUID.randomUUID(), failedOrder, "5000"));
        UUID failedInvoiceId = jdbc.queryForObject("select id from invoices where order_id = ?", UUID.class,
                failedOrder.toString());

        restTestClient.post().uri("/api/invoices/{id}/issue", failedInvoiceId)
                .exchange()
                .expectStatus().isEqualTo(409);
        restTestClient.post().uri("/api/invoices/{id}/cancel", failedInvoiceId)
                .exchange()
                .expectStatus().isEqualTo(409);
        restTestClient.get().uri("/api/invoices/{id}", failedInvoiceId)
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
