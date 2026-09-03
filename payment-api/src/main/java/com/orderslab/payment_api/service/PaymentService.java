package com.orderslab.payment_api.service;

import com.orderslab.payment_api.dto.PaymentReservationRequest;
import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.exception.PaymentNotFoundException;
import com.orderslab.payment_api.model.Payment;
import com.orderslab.payment_api.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    // Armazenamento em memória só para o exercício.
    // Depois pode virar um repository (JPA) apontando pro banco da Payments API.
    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();

    public PaymentResponse reserve(PaymentReservationRequest request) {
        Payment payment = new Payment(request.getOrderId(), request.getAmount());
        payment.setStatus(PaymentStatus.RESERVED);
        payments.put(payment.getId(), payment);
        return toResponse(payment);
    }

    public PaymentResponse confirm(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        payment.setStatus(PaymentStatus.CONFIRMED);
        return toResponse(payment);
    }

    public PaymentResponse cancel(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        payment.setStatus(PaymentStatus.CANCELLED);
        return toResponse(payment);
    }

    public PaymentResponse findById(UUID paymentId) {
        return toResponse(findOrThrow(paymentId));
    }

    public List<PaymentResponse> findAll() {
        return payments.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Payment findOrThrow(UUID paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException(paymentId);
        }
        return payment;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}