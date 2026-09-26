package com.orderslab.invoice_api.service;

import com.orderslab.invoice_api.dto.InvoiceRequest;
import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.exception.InvalidStatusTransitionException;
import com.orderslab.invoice_api.exception.InvoiceNotFoundException;
import com.orderslab.invoice_api.model.Invoice;
import com.orderslab.invoice_api.model.InvoiceStatus;
import com.orderslab.invoice_api.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public InvoiceResponse create(InvoiceRequest request) {
        Invoice invoice = new Invoice(request.getOrderId(), request.getPaymentId(), request.getAmount());
        invoiceRepository.save(invoice);
        log.atInfo()
                .addKeyValue("invoiceId", invoice.getId())
                .addKeyValue("status", invoice.getStatus())
                .log("Invoice created");
        return toResponse(invoice);
    }

    public InvoiceResponse issue(UUID invoiceId) {
        Invoice invoice = findOrThrow(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new InvalidStatusTransitionException(invoice.getStatus(), "ser emitida");
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoiceRepository.save(invoice);
        log.atInfo()
                .addKeyValue("invoiceId", invoice.getId())
                .addKeyValue("status", invoice.getStatus())
                .log("Invoice issued");
        return toResponse(invoice);
    }

    public InvoiceResponse cancel(UUID invoiceId) {
        Invoice invoice = findOrThrow(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new InvalidStatusTransitionException(invoice.getStatus(), "ser cancelada");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
        log.atInfo()
                .addKeyValue("invoiceId", invoice.getId())
                .addKeyValue("status", invoice.getStatus())
                .log("Invoice cancelled");
        return toResponse(invoice);
    }

    public InvoiceResponse findById(UUID invoiceId) {
        return toResponse(findOrThrow(invoiceId));
    }

    public List<InvoiceResponse> findAll() {
        return invoiceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Invoice findOrThrow(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
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
