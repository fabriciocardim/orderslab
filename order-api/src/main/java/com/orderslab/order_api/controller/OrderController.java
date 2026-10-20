package com.orderslab.order_api.controller;

import com.orderslab.order_api.dto.OrderPage;
import com.orderslab.order_api.dto.OrderRequest;
import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Os {@code limit} mais recentes (padrão 200, de 1 a 500), com o total no cabeçalho X-Total-Count. */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> find(
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
        OrderPage page = orderService.findRecent(limit);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, String.valueOf(page.total())).body(page.items());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}
