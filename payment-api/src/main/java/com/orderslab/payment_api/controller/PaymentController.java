package com.orderslab.payment_api.controller;

import com.orderslab.payment_api.dto.PaymentPage;
import com.orderslab.payment_api.dto.PaymentReservationRequest;
import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> reserve(@Valid @RequestBody PaymentReservationRequest request) {
        PaymentResponse response = paymentService.reserve(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Com {@code orderId}: só os registros desse pedido (o polling da tela de acompanhamento). Sem: os {@code limit}
     * mais recentes (padrão 200, de 1 a 500), com o total no cabeçalho X-Total-Count.
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> find(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
        if (orderId != null) {
            return ResponseEntity.ok(paymentService.findByOrderId(orderId));
        }
        PaymentPage page = paymentService.findRecent(limit);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, String.valueOf(page.total())).body(page.items());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.findById(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PaymentResponse> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.cancel(id));
    }
}