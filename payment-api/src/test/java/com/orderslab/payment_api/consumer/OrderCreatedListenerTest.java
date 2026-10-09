package com.orderslab.payment_api.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.orderslab.payment_api.event.OrderCreatedMessage;
import com.orderslab.payment_api.processing.OrderCreatedProcessor;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    @Mock
    private OrderCreatedProcessor processor;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private OrderCreatedListener listener() {
        return new OrderCreatedListener(jsonMapper, processor);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("order.created", 0, 42L, "key", value);
    }

    @Test
    void shouldPassValidMessageToProcessor() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"OrderCreated\",\"eventVersion\":1,"
                + "\"orderId\":\"" + orderId + "\",\"customerId\":\"c1\",\"amount\":10.50,\"novo\":true}";

        listener().onMessage(record(json));

        ArgumentCaptor<OrderCreatedMessage> captor = ArgumentCaptor.forClass(OrderCreatedMessage.class);
        verify(processor).process(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("10.50");
    }

    @Test
    void shouldRejectUnreadableOrIncompleteMessagesAsInvalidWithoutCallingTheProcessor() {
        String ok = UUID.randomUUID().toString();
        String[] bad = {
            "lixo-nao-json",
            "{}",
            "{\"orderId\":\"" + ok + "\",\"amount\":10}",
            "{\"eventId\":\"" + ok + "\",\"amount\":10}",
            "{\"eventId\":\"" + ok + "\",\"orderId\":\"" + ok + "\"}",
            "{\"eventId\":\"" + ok + "\",\"orderId\":\"nao-e-uuid\",\"amount\":10}",
            "{\"eventId\":\"" + ok + "\",\"orderId\":\"" + ok + "\",\"amount\":0}",
            "{\"eventId\":\"" + ok + "\",\"orderId\":\"" + ok + "\",\"amount\":-5}",
            null
        };

        for (String value : bad) {
            assertThatThrownBy(() -> listener().onMessage(record(value)))
                    .as("valor: %s", value)
                    .isInstanceOf(InvalidMessageException.class);
        }

        verify(processor, never()).process(any());
    }

    @Test
    void shouldPropagateTransientProcessorFailure() {
        String ok = UUID.randomUUID().toString();
        String json = "{\"eventId\":\"" + ok + "\",\"orderId\":\"" + ok + "\",\"amount\":10}";
        doThrow(new DataAccessResourceFailureException("db down")).when(processor).process(any());

        assertThatThrownBy(() -> listener().onMessage(record(json)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
