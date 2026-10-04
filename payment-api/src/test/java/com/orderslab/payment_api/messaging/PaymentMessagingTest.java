package com.orderslab.payment_api.messaging;

import static com.orderslab.payment_api.messaging.KafkaTestSupport.assertNoMore;
import static com.orderslab.payment_api.messaging.KafkaTestSupport.awaitRecords;
import static com.orderslab.payment_api.messaging.KafkaTestSupport.header;
import static com.orderslab.payment_api.messaging.KafkaTestSupport.headerNames;
import static com.orderslab.payment_api.messaging.KafkaTestSupport.partitionCount;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensageria do payment-api contra Kafka e Postgres reais (E2.6): automatiza a validação manual do E2.3 e do
 * DLT do E2.5. O retry é lento de propósito (60 s de espera inicial, teto 120 s): se uma mensagem inválida fosse
 * repetida antes de ir ao DLT, ela levaria mais de 7 min e estouraria com folga o prazo de 45 s dos testes,
 * que por sua vez tolera picos de latência do broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"outbox.relay.interval-ms=200", "consumer.retry.initial-interval-ms=60000",
                "consumer.retry.max-interval-ms=120000"})
@Testcontainers
class PaymentMessagingTest {

    private static final Duration WAIT = Duration.ofSeconds(45);
    private static final Duration DLT_WAIT = Duration.ofSeconds(45);
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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private String bootstrap() {
        return kafka.getBootstrapServers();
    }

    private static String orderCreated(UUID eventId, UUID orderId, String amount) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"OrderCreated\",\"eventVersion\":1,"
                + "\"occurredAt\":\"2026-10-04T10:00:00Z\",\"orderId\":\"" + orderId
                + "\",\"customerId\":\"cliente-msg\",\"amount\":" + amount + "}";
    }

    /**
     * Publica no broker real. Logo após o broker subir, o metadata do tópico pode demorar mais que o
     * max.block.ms de produção (5 s) e o send lança a TimeoutException do Kafka *antes* de enviar nada; nesse
     * caso insiste até um prazo (não há risco de duplicar, pois nada foi enviado).
     */
    private void publish(String key, String value) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (true) {
            try {
                kafkaTemplate.send("order.created", key, value).get(15, TimeUnit.SECONDS);
                return;
            } catch (org.apache.kafka.common.errors.TimeoutException brokerStillWarmingUp) {
                if (Instant.now().isAfter(deadline)) {
                    throw new AssertionError("broker não respondeu ao publicar em order.created em 60 s", brokerStillWarmingUp);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrompido ao publicar em order.created", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new AssertionError("falha ao publicar em order.created", e);
            }
        }
    }

    /**
     * Espera o evento da chave e devolve uma cópia. A entrega é pelo menos uma vez: cópias idênticas do mesmo
     * evento (reenvio após timeout de ack) são aceitas; dois eventos <i>diferentes</i> para a mesma chave não.
     */
    private ConsumerRecord<String, String> single(String topic, String key, Duration timeout) {
        List<ConsumerRecord<String, String>> records = awaitRecords(bootstrap(), topic, key, 1, timeout);
        assertThat(KafkaTestSupport.distinctValues(records))
                .as("registros de %s com chave %s", topic, key).isEqualTo(1);
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

    private String paymentStatus(UUID orderId) {
        return jdbc.queryForObject("select status from payments where order_id = ?", String.class,
                orderId.toString());
    }

    private int paymentCount(UUID orderId) {
        return jdbc.queryForObject("select count(*) from payments where order_id = ?", Integer.class,
                orderId.toString());
    }

    @Test
    void lowAmountBecomesReservedAndPublishedOnPaymentReserved() {
        UUID orderId = UUID.randomUUID();
        UUID edgeOrderId = UUID.randomUUID();

        publish(orderId.toString(), orderCreated(UUID.randomUUID(), orderId, "10.50"));
        publish(edgeOrderId.toString(), orderCreated(UUID.randomUUID(), edgeOrderId, "1000"));

        JsonNode reserved = assertEnvelope(single("payment.reserved", orderId.toString(), WAIT),
                "PaymentReserved", orderId);
        assertThat(UUID.fromString(reserved.get("paymentId").asString())).isNotNull();
        assertThat(reserved.get("amount").decimalValue()).isEqualByComparingTo("10.50");
        assertThat(paymentStatus(orderId)).isEqualTo("RESERVED");
        assertEnvelope(single("payment.reserved", edgeOrderId.toString(), WAIT), "PaymentReserved", edgeOrderId);
        assertThat(paymentStatus(edgeOrderId)).as("exatamente no limite é reservado").isEqualTo("RESERVED");
    }

    @Test
    void highAmountBecomesFailedAndPublishedOnPaymentFailed() {
        UUID orderId = UUID.randomUUID();

        publish(orderId.toString(), orderCreated(UUID.randomUUID(), orderId, "1500"));

        JsonNode failed = assertEnvelope(single("payment.failed", orderId.toString(), WAIT),
                "PaymentFailed", orderId);
        assertThat(failed.get("reason").asString()).isEqualTo("AMOUNT_LIMIT_EXCEEDED");
        assertThat(paymentStatus(orderId)).isEqualTo("FAILED");
        assertNoMore(bootstrap(), "payment.reserved", orderId.toString(), 0, QUIET);
    }

    @Test
    void redeliveryOfTheSameEventDoesNotDuplicate() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            publish(orderId.toString(), orderCreated(eventId, orderId, "10"));
        }
        publish(orderId.toString(), orderCreated(UUID.randomUUID(), orderId, "10")); // outro eventId, mesmo pedido

        single("payment.reserved", orderId.toString(), WAIT);
        assertNoMore(bootstrap(), "payment.reserved", orderId.toString(), 1, QUIET);
        assertThat(paymentCount(orderId)).as("um único pagamento por pedido").isEqualTo(1);
    }

    @Test
    void invalidMessagesGoStraightToTheDltAndTheNextOneIsProcessed() {
        UUID validOrder = UUID.randomUUID();
        String[] invalid = {
            "lixo-nao-json",
            "{\"eventId\":\"x\",\"amount\":5}",
            "{\"amount\":5}",
            orderCreated(UUID.randomUUID(), UUID.randomUUID(), "0")
        };
        String[] keys = new String[invalid.length];
        for (int i = 0; i < invalid.length; i++) {
            keys[i] = "bad-" + UUID.randomUUID();
            publish(keys[i], invalid[i]);
        }
        publish(validOrder.toString(), orderCreated(UUID.randomUUID(), validOrder, "10"));

        for (int i = 0; i < invalid.length; i++) {
            ConsumerRecord<String, String> dead = single("order.created.dlt", keys[i], DLT_WAIT);
            assertThat(dead.value()).as("valor idêntico ao original").isEqualTo(invalid[i]);
            assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo("order.created");
            assertThat(header(dead, "kafka_dlt-original-offset")).isNotNull();
            assertThat(header(dead, "kafka_dlt-exception-message")).isNotBlank();
        }
        assertEnvelope(single("payment.reserved", validOrder.toString(), WAIT), "PaymentReserved", validOrder);
        assertThat(paymentStatus(validOrder)).isEqualTo("RESERVED");
    }

    @Test
    void outputAndDltTopicsHaveThreePartitions() {
        assertThat(partitionCount(bootstrap(), "payment.reserved")).isEqualTo(3);
        assertThat(partitionCount(bootstrap(), "payment.failed")).isEqualTo(3);
        assertThat(partitionCount(bootstrap(), "order.created.dlt")).isEqualTo(3);
    }
}
