package com.orderslab.payment_api.service;

import com.orderslab.payment_api.dto.PaymentReservationRequest;
import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.exception.InvalidStatusTransitionException;
import com.orderslab.payment_api.exception.PaymentNotFoundException;
import com.orderslab.payment_api.model.Payment;
import com.orderslab.payment_api.model.PaymentStatus;
import com.orderslab.payment_api.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse reserve(PaymentReservationRequest request) {
        Payment payment = new Payment(request.getOrderId(), request.getAmount());
        paymentRepository.save(payment);
        log.atInfo()
                .addKeyValue("paymentId", payment.getId())
                .addKeyValue("status", payment.getStatus())
                .log("Payment reserved");
        return toResponse(payment);
    }

    public PaymentResponse confirm(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        if (payment.getStatus() != PaymentStatus.RESERVED) {
            throw new InvalidStatusTransitionException(payment.getStatus(), "ser confirmado");
        }
        payment.setStatus(PaymentStatus.CONFIRMED);
        paymentRepository.save(payment);
        log.atInfo()
                .addKeyValue("paymentId", payment.getId())
                .addKeyValue("status", payment.getStatus())
                .log("Payment confirmed");
        return toResponse(payment);
    }

    public PaymentResponse cancel(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        if (payment.getStatus() != PaymentStatus.RESERVED) {
            throw new InvalidStatusTransitionException(payment.getStatus(), "ser cancelado");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        log.atInfo()
                .addKeyValue("paymentId", payment.getId())
                .addKeyValue("status", payment.getStatus())
                .log("Payment cancelled");
        return toResponse(payment);
    }

    public PaymentResponse findById(UUID paymentId) {
        return toResponse(findOrThrow(paymentId));
    }

    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Payment findOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
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
