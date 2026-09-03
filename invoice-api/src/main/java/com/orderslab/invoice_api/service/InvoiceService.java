package com.orderslab.invoice_api.service;

import com.orderslab.invoice_api.dto.InvoiceRequest;
import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.exception.InvoiceNotFoundException;
import com.orderslab.invoice_api.model.Invoice;
import com.orderslab.invoice_api.model.InvoiceStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    // Armazenamento em memória só para o exercício.
    // Depois pode virar um repository (JPA) apontando pro banco da Invoice API.
    private final Map<UUID, Invoice> invoices = new ConcurrentHashMap<>();

    public InvoiceResponse create(InvoiceRequest request) {
        Invoice invoice = new Invoice(request.getOrderId(), request.getPaymentId(), request.getAmount());
        invoices.put(invoice.getId(), invoice);
        return toResponse(invoice);
    }

    public InvoiceResponse issue(UUID invoiceId) {
        Invoice invoice = findOrThrow(invoiceId);
        invoice.setStatus(InvoiceStatus.ISSUED);
        return toResponse(invoice);
    }

    public InvoiceResponse cancel(UUID invoiceId) {
        Invoice invoice = findOrThrow(invoiceId);
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return toResponse(invoice);
    }

    public InvoiceResponse findById(UUID invoiceId) {
        return toResponse(findOrThrow(invoiceId));
    }

    public List<InvoiceResponse> findAll() {
        return invoices.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Invoice findOrThrow(UUID invoiceId) {
        Invoice invoice = invoices.get(invoiceId);
        if (invoice == null) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        return invoice;
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getOrderId(),
                invoice.getPaymentId(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
