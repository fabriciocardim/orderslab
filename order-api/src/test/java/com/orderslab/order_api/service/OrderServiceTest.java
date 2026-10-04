package com.orderslab.order_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.orderslab.order_api.config.KafkaTopicsConfig;
import com.orderslab.order_api.dto.OrderRequest;
import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.event.OrderCancelled;
import com.orderslab.order_api.event.OrderConfirmed;
import com.orderslab.order_api.event.OrderCreated;
import com.orderslab.order_api.exception.InvalidStatusTransitionException;
import com.orderslab.order_api.exception.OrderNotFoundException;
import com.orderslab.order_api.model.Order;
import com.orderslab.order_api.model.OrderStatus;
import com.orderslab.order_api.outbox.OutboxWriter;
import com.orderslab.order_api.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @InjectMocks
    private OrderService orderService;

    private final Map<UUID, Order> savedOrders = new HashMap<>();

    @BeforeEach
    void setUp() {
        savedOrders.clear();
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            savedOrders.put(order.getId(), order);
            return order;
        });
        lenient().when(orderRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(savedOrders.get(id));
        });
        lenient().when(orderRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(savedOrders.get(id));
        });
        lenient().when(orderRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(savedOrders.values()));
    }

    private OrderResponse createOrder() {
        return orderService.create(new OrderRequest("cliente-1", BigDecimal.TEN));
    }

    @Test
    void shouldCreateWithInitialStatus() {
        OrderResponse response = createOrder();

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo("cliente-1");
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldFindByIdWhenExists() {
        OrderResponse created = createOrder();

        OrderResponse found = orderService.findById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void shouldThrowNotFoundWhenIdDoesNotExist() {
        assertThrows(OrderNotFoundException.class, () -> orderService.findById(UUID.randomUUID()));
    }

    @Test
    void shouldThrowNotFoundOnConfirmWhenIdDoesNotExist() {
        assertThrows(OrderNotFoundException.class, () -> orderService.confirm(UUID.randomUUID()));
    }

    @Test
    void shouldThrowNotFoundOnCancelWhenIdDoesNotExist() {
        assertThrows(OrderNotFoundException.class, () -> orderService.cancel(UUID.randomUUID()));
    }

    @Test
    void shouldConfirmWhenPending() {
        OrderResponse created = createOrder();

        OrderResponse confirmed = orderService.confirm(created.getId());

        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldCancelWhenPending() {
        OrderResponse created = createOrder();

        OrderResponse cancelled = orderService.cancel(created.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldThrowInvalidTransitionWhenConfirmingAlreadyCancelled() {
        OrderResponse created = createOrder();
        orderService.cancel(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> orderService.confirm(created.getId()));
    }

    @Test
    void shouldThrowInvalidTransitionWhenCancellingAlreadyConfirmed() {
        OrderResponse created = createOrder();
        orderService.confirm(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> orderService.cancel(created.getId()));
    }

    @Test
    void shouldReturnEmptyListWhenNoneCreated() {
        assertThat(orderService.findAll()).isEmpty();
    }

    @Test
    void createShouldEnqueueOrderCreated() {
        OrderResponse created = createOrder();

        ArgumentCaptor<OrderCreated> captor = ArgumentCaptor.forClass(OrderCreated.class);
        verify(outboxWriter).enqueue(captor.capture(), eq(KafkaTopicsConfig.ORDER_CREATED));
        assertThat(captor.getValue().orderId()).isEqualTo(created.getId());
        assertThat(captor.getValue().customerId()).isEqualTo("cliente-1");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(captor.getValue().occurredAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    void confirmShouldLockOrderAndEnqueueOrderConfirmed() {
        OrderResponse created = createOrder();

        orderService.confirm(created.getId());

        verify(orderRepository).findByIdForUpdate(created.getId());
        ArgumentCaptor<OrderConfirmed> captor = ArgumentCaptor.forClass(OrderConfirmed.class);
        verify(outboxWriter).enqueue(captor.capture(), eq(KafkaTopicsConfig.ORDER_CONFIRMED));
        assertThat(captor.getValue().orderId()).isEqualTo(created.getId());
    }

    @Test
    void cancelShouldLockOrderAndEnqueueOrderCancelled() {
        OrderResponse created = createOrder();

        orderService.cancel(created.getId());

        verify(orderRepository).findByIdForUpdate(created.getId());
        ArgumentCaptor<OrderCancelled> captor = ArgumentCaptor.forClass(OrderCancelled.class);
        verify(outboxWriter).enqueue(captor.capture(), eq(KafkaTopicsConfig.ORDER_CANCELLED));
        assertThat(captor.getValue().orderId()).isEqualTo(created.getId());
    }

    @Test
    void rejectedTransitionShouldNotEnqueueAnyEvent() {
        OrderResponse created = createOrder();
        orderService.cancel(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> orderService.confirm(created.getId()));

        verify(outboxWriter, never()).enqueue(any(OrderConfirmed.class), eq(KafkaTopicsConfig.ORDER_CONFIRMED));
    }
}
