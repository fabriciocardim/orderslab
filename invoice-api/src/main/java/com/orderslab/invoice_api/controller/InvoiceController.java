package com.orderslab.invoice_api.controller;

import com.orderslab.invoice_api.dto.InvoicePage;
import com.orderslab.invoice_api.dto.InvoiceRequest;
import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.service.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
        InvoiceResponse response = invoiceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Com {@code orderId}: só os registros desse pedido (o polling da tela de acompanhamento). Sem: os {@code limit}
     * mais recentes (padrão 200, de 1 a 500), com o total no cabeçalho X-Total-Count.
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> find(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
        if (orderId != null) {
            return ResponseEntity.ok(invoiceService.findByOrderId(orderId));
        }
        InvoicePage page = invoiceService.findRecent(limit);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, String.valueOf(page.total())).body(page.items());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.findById(id));
    }

    @PostMapping("/{id}/issue")
    public ResponseEntity<InvoiceResponse> issue(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.issue(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<InvoiceResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.cancel(id));
    }
}
