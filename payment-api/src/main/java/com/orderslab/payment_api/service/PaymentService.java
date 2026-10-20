package com.orderslab.payment_api.service;

import com.orderslab.payment_api.dto.PaymentPage;
import com.orderslab.payment_api.dto.PaymentReservationRequest;
import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.exception.InvalidStatusTransitionException;
import com.orderslab.payment_api.exception.PaymentNotFoundException;
import com.orderslab.payment_api.model.Payment;
import com.orderslab.payment_api.model.PaymentStatus;
import com.orderslab.payment_api.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    public List<PaymentResponse> findByOrderId(UUID orderId) {
        // order_id é VARCHAR com o UUID em minúsculas (como UUID.toString())
        List<PaymentResponse> found = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId.toString()).stream()
                .map(this::toResponse)
                .toList();
        log.atDebug()
                .addKeyValue("orderId", orderId)
                .addKeyValue("count", found.size())
                .log("Payments listed by order");
        return found;
    }

    public PaymentPage findRecent(int limit) {
        Page<Payment> page = paymentRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
        log.atDebug()
                .addKeyValue("limit", limit)
                .addKeyValue("total", page.getTotalElements())
                .log("Recent payments listed");
        return new PaymentPage(page.map(this::toResponse).getContent(), page.getTotalElements());
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
