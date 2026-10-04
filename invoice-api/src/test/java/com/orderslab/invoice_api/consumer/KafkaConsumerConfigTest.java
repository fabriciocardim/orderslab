package com.orderslab.invoice_api.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderslab.invoice_api.config.ConsumerRetryProperties;
import com.orderslab.invoice_api.config.KafkaConsumerConfig;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.BackOffExecution;
import tools.jackson.databind.json.JsonMapper;

class KafkaConsumerConfigTest {

    private static final String VALUE = "{\"eventId\":\"e1\",\"orderId\":\"o1\",\"amount\":10}";

    private KafkaTemplate<String, String> kafkaTemplate;
    private Consumer<?, ?> consumer;
    private MessageListenerContainer container;
    private DefaultErrorHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        consumer = mock(Consumer.class);
        container = mock(MessageListenerContainer.class);
        lenient().when(container.isRunning()).thenReturn(true);
        lenient().when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(sendOk());
        // esperas minúsculas para o teste não dormir: 4 retentativas, 1 ms, x2, teto 8 ms
        handler = (DefaultErrorHandler) new KafkaConsumerConfig().kafkaErrorHandler(
                kafkaTemplate, new ConsumerRetryProperties(4, 1, 2.0, 8), JsonMapper.builder().build());
    }

    private static CompletableFuture<SendResult<String, String>> sendOk() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("payment.reserved.dlt", 0), 0, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("payment.reserved", 1, 7L, "order-key", value);
    }

    private static ListenerExecutionFailedException wrap(Throwable cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    private boolean handle(ConsumerRecord<String, String> record, Throwable cause) {
        return handler.handleOne(wrap(cause), record, consumer, container);
    }

    @Test
    void transientFailureShouldRetryFourTimesAndOnlyThenGoToTheDlt() {
        ConsumerRecord<String, String> record = record(VALUE);
        RuntimeException failure = new IllegalStateException("db down");

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(handle(record, failure)).as("tentativa %d ainda não recupera", attempt).isFalse();
            verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        }
        assertThat(handle(record, failure)).as("5ª falha esgota o retry e estaciona").isTrue();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("payment.reserved.dlt");
    }

    @Test
    void dltMessageShouldKeepKeyAndValueAndCarryDiagnosticHeaders() {
        ConsumerRecord<String, String> record = record(VALUE);

        handle(record, new InvalidMessageException("unreadable JSON"));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(sent.capture());
        ProducerRecord<String, String> dlt = sent.getValue();
        assertThat(dlt.topic()).isEqualTo("payment.reserved.dlt");
        assertThat(dlt.key()).isEqualTo("order-key");
        assertThat(dlt.value()).isEqualTo(VALUE);
        assertThat(dlt.partition()).as("partição escolhida pela chave").isNull();
        assertThat(header(dlt, "kafka_dlt-original-topic")).isEqualTo("payment.reserved");
        assertThat(header(dlt, "kafka_dlt-original-partition")).isNotNull();
        assertThat(header(dlt, "kafka_dlt-original-offset")).isNotNull();
        assertThat(header(dlt, "kafka_dlt-exception-message")).contains("unreadable JSON");
    }

    @Test
    void invalidMessageShouldGoStraightToTheDltWithoutRetry() {
        assertThat(handle(record(VALUE), new InvalidMessageException("missing required field"))).isTrue();

        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    void nullValueShouldBeParkedToo() {
        assertThat(handle(record(null), new InvalidMessageException("null value"))).isTrue();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(sent.capture());
        assertThat(sent.getValue().value()).isNull();
        assertThat(sent.getValue().key()).isEqualTo("order-key");
    }

    @Test
    void failureSendingToTheDltShouldNotCountAsRecovered() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("dlt unavailable")));

        boolean recovered = handle(record(VALUE), new InvalidMessageException("bad"));

        assertThat(recovered).as("a mensagem não é dada como recuperada: será reentregue").isFalse();
    }

    @Test
    void backOffShouldGrowExponentiallyAndStopAfterMaxRetries() {
        BackOffExecution execution = KafkaConsumerConfig
                .backOff(new ConsumerRetryProperties(4, 1000, 2.0, 10000)).start();

        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(2000L);
        assertThat(execution.nextBackOff()).isEqualTo(4000L);
        assertThat(execution.nextBackOff()).isEqualTo(8000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void backOffShouldRespectTheMaximumInterval() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerConfig
                .backOff(new ConsumerRetryProperties(4, 1000, 2.0, 3000));
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(2000L);
        assertThat(execution.nextBackOff()).isEqualTo(3000L);
        assertThat(execution.nextBackOff()).isEqualTo(3000L);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
