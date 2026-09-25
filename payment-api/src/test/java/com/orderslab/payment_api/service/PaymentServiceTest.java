package com.orderslab.payment_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.orderslab.payment_api.dto.PaymentReservationRequest;
import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.exception.InvalidStatusTransitionException;
import com.orderslab.payment_api.exception.PaymentNotFoundException;
import com.orderslab.payment_api.model.Payment;
import com.orderslab.payment_api.model.PaymentStatus;
import com.orderslab.payment_api.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private final Map<UUID, Payment> savedPayments = new HashMap<>();

    @BeforeEach
    void setUp() {
        savedPayments.clear();
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            savedPayments.put(payment.getId(), payment);
            return payment;
        });
        lenient().when(paymentRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(savedPayments.get(id));
        });
        lenient().when(paymentRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(savedPayments.values()));
    }

    private PaymentResponse reservePayment() {
        return paymentService.reserve(new PaymentReservationRequest(ORDER_ID, BigDecimal.TEN));
    }

    @Test
    void shouldReserveWithInitialStatus() {
        PaymentResponse response = reservePayment();

        assertThat(response.getId()).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.RESERVED);
    }

    @Test
    void shouldFindByIdWhenExists() {
        PaymentResponse created = reservePayment();

        PaymentResponse found = paymentService.findById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void shouldThrowNotFoundWhenIdDoesNotExist() {
        assertThrows(PaymentNotFoundException.class, () -> paymentService.findById(UUID.randomUUID()));
    }

    @Test
    void shouldThrowNotFoundOnConfirmWhenIdDoesNotExist() {
        assertThrows(PaymentNotFoundException.class, () -> paymentService.confirm(UUID.randomUUID()));
    }

    @Test
    void shouldThrowNotFoundOnCancelWhenIdDoesNotExist() {
        assertThrows(PaymentNotFoundException.class, () -> paymentService.cancel(UUID.randomUUID()));
    }

    @Test
    void shouldConfirmWhenReserved() {
        PaymentResponse created = reservePayment();

        PaymentResponse confirmed = paymentService.confirm(created.getId());

        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void shouldCancelWhenReserved() {
        PaymentResponse created = reservePayment();

        PaymentResponse cancelled = paymentService.cancel(created.getId());

        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void shouldThrowInvalidTransitionWhenConfirmingAlreadyCancelled() {
        PaymentResponse created = reservePayment();
        paymentService.cancel(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> paymentService.confirm(created.getId()));
    }

    @Test
    void shouldThrowInvalidTransitionWhenCancellingAlreadyConfirmed() {
        PaymentResponse created = reservePayment();
        paymentService.confirm(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> paymentService.cancel(created.getId()));
    }

    @Test
    void shouldReturnEmptyListWhenNoneCreated() {
        assertThat(paymentService.findAll()).isEmpty();
    }
}
