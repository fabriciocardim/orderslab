package com.orderslab.invoice_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderslab.invoice_api.dto.InvoiceRequest;
import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.exception.InvalidStatusTransitionException;
import com.orderslab.invoice_api.exception.InvoiceNotFoundException;
import com.orderslab.invoice_api.model.InvoiceStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String PAYMENT_ID = "a8541a91-1fcc-4b2e-9fdc-aa23f090043a";

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService();
    }

    private InvoiceResponse createInvoice() {
        return invoiceService.create(new InvoiceRequest(ORDER_ID, PAYMENT_ID, BigDecimal.TEN));
    }

    @Test
    void shouldCreateWithInitialStatus() {
        InvoiceResponse response = createInvoice();

        assertThat(response.getId()).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void shouldFindByIdWhenExists() {
        InvoiceResponse created = createInvoice();

        InvoiceResponse found = invoiceService.findById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void shouldThrowNotFoundWhenIdDoesNotExist() {
        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.findById(UUID.randomUUID()));
    }

    @Test
    void shouldThrowNotFoundOnIssueWhenIdDoesNotExist() {
        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.issue(UUID.randomUUID()));
    }

    @Test
    void shouldThrowNotFoundOnCancelWhenIdDoesNotExist() {
        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.cancel(UUID.randomUUID()));
    }

    @Test
    void shouldIssueWhenPending() {
        InvoiceResponse created = createInvoice();

        InvoiceResponse issued = invoiceService.issue(created.getId());

        assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void shouldCancelWhenPending() {
        InvoiceResponse created = createInvoice();

        InvoiceResponse cancelled = invoiceService.cancel(created.getId());

        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void shouldThrowInvalidTransitionWhenIssuingAlreadyCancelled() {
        InvoiceResponse created = createInvoice();
        invoiceService.cancel(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> invoiceService.issue(created.getId()));
    }

    @Test
    void shouldThrowInvalidTransitionWhenCancellingAlreadyIssued() {
        InvoiceResponse created = createInvoice();
        invoiceService.issue(created.getId());

        assertThrows(InvalidStatusTransitionException.class, () -> invoiceService.cancel(created.getId()));
    }

    @Test
    void shouldReturnEmptyListWhenNoneCreated() {
        assertThat(invoiceService.findAll()).isEmpty();
    }
}
