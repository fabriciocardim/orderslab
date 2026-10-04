package com.orderslab.invoice_api.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Auxiliar de teste (cópia própria deste serviço — nada compartilhado entre serviços): lê tópicos de um
 * Kafka real por polling com prazo, filtrando pela chave, e falha com mensagem descritiva.
 */
final class KafkaTestSupport {

    private static final Duration POLL = Duration.ofMillis(200);

    private KafkaTestSupport() {
    }

    /** Consumidor que lê todas as partições do tópico desde o início, aguardando o tópico existir. */
    static KafkaConsumer<String, String> consumerFor(String bootstrapServers, String topic, Instant deadline) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        try {
            List<TopicPartition> partitions = new ArrayList<>();
            while (partitions.isEmpty()) {
                try {
                    consumer.partitionsFor(topic, Duration.ofSeconds(5))
                            .forEach(p -> partitions.add(new TopicPartition(topic, p.partition())));
                } catch (org.apache.kafka.common.errors.TimeoutException brokerBusy) {
                    // broker momentaneamente lento (metadata): tenta de novo até o prazo
                    if (Instant.now().isAfter(deadline)) {
                        throw new AssertionError("broker não respondeu o metadata de " + topic + " até o prazo",
                                brokerBusy);
                    }
                }
                if (partitions.isEmpty()) {
                    if (Instant.now().isAfter(deadline)) {
                        throw new AssertionError("tópico " + topic + " não existe no broker");
                    }
                    pause(100);
                }
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            return consumer;
        } catch (RuntimeException | Error e) {
            consumer.close();
            throw e;
        }
    }

    /** Espera (polling com prazo) até chegarem {@code expected} registros do tópico com a chave dada. */
    static List<ConsumerRecord<String, String>> awaitRecords(String bootstrapServers, String topic, String key,
                                                            int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumerFor(bootstrapServers, topic, deadline)) {
            while (found.size() < expected && Instant.now().isBefore(deadline)) {
                collect(consumer.poll(POLL), key, found);
            }
        }
        if (found.size() < expected) {
            throw new AssertionError("esperava " + expected + " registro(s) com chave " + key + " em " + topic
                    + " em " + timeout + "; chegaram " + found.size());
        }
        return found;
    }

    /**
     * Asserção negativa: espera (polling com prazo) os {@code expectedEvents} eventos da chave já existentes e,
     * a partir daí, observa por {@code quietPeriod} que não surge nenhum evento <b>distinto</b> além deles. A
     * entrega é pelo menos uma vez: cópias idênticas do mesmo evento (mesmo valor/{@code eventId}) são
     * reentregas aceitas pelo contrato e não contam como evento novo.
     */
    static void assertNoMore(String bootstrapServers, String topic, String key, int expectedEvents,
                             Duration quietPeriod) {
        Instant arrivalDeadline = Instant.now().plusSeconds(60);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumerFor(bootstrapServers, topic, arrivalDeadline)) {
            while (distinctValues(found) < expectedEvents && Instant.now().isBefore(arrivalDeadline)) {
                collect(consumer.poll(POLL), key, found);
            }
            Instant quietEnd = Instant.now().plus(quietPeriod);
            do {
                collect(consumer.poll(POLL), key, found);
            } while (Instant.now().isBefore(quietEnd));
        }
        if (distinctValues(found) != expectedEvents) {
            throw new AssertionError("esperava exatamente " + expectedEvents + " evento(s) distinto(s) com chave "
                    + key + " em " + topic + "; encontrei " + distinctValues(found)
                    + " (cópias do mesmo evento são aceitas: entrega pelo menos uma vez)");
        }
    }

    /** Eventos distintos entre os registros: cópias reentregues têm exatamente o mesmo valor. */
    static long distinctValues(List<ConsumerRecord<String, String>> records) {
        return records.stream().map(ConsumerRecord::value).distinct().count();
    }

    static Set<String> headerNames(ConsumerRecord<?, ?> record) {
        return StreamSupport.stream(record.headers().spliterator(), false)
                .map(Header::key).collect(Collectors.toSet());
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    static int partitionCount(String bootstrapServers, String topic) {
        try (AdminClient admin = AdminClient
                .create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            return admin.describeTopics(List.of(topic)).allTopicNames().get(30, TimeUnit.SECONDS)
                    .get(topic).partitions().size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrompido ao descrever o tópico " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("não foi possível descrever o tópico " + topic, e);
        }
    }

    private static void collect(ConsumerRecords<String, String> records, String key,
                                List<ConsumerRecord<String, String>> into) {
        for (ConsumerRecord<String, String> record : records) {
            if (key.equals(record.key())) {
                into.add(record);
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
