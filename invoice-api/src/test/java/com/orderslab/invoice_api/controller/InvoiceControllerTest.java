package com.orderslab.invoice_api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.exception.InvalidStatusTransitionException;
import com.orderslab.invoice_api.exception.InvoiceNotFoundException;
import com.orderslab.invoice_api.model.InvoiceStatus;
import com.orderslab.invoice_api.service.InvoiceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String PAYMENT_ID = "a8541a91-1fcc-4b2e-9fdc-aa23f090043a";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    private InvoiceResponse sampleResponse(UUID id, InvoiceStatus status) {
        return new InvoiceResponse(id, ORDER_ID, PAYMENT_ID, BigDecimal.TEN, status, Instant.now(), Instant.now());
    }

    @Test
    void shouldReturn201WhenCreatingWithValidBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.create(any())).thenReturn(sampleResponse(id, InvoiceStatus.PENDING));

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\",\"paymentId\":\"" + PAYMENT_ID + "\",\"amount\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturn400WhenCreatingWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());

        verify(invoiceService, never()).create(any());
    }

    @Test
    void shouldReturn200WhenFindingByIdExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.findById(id)).thenReturn(sampleResponse(id, InvoiceStatus.PENDING));

        mockMvc.perform(get("/api/invoices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void shouldReturn404WhenFindingByIdDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.findById(id)).thenThrow(new InvoiceNotFoundException(id));

        mockMvc.perform(get("/api/invoices/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/invoices/" + id));
    }

    @Test
    void shouldReturn200WhenListingResources() throws Exception {
        when(invoiceService.findAll()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), InvoiceStatus.PENDING)));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldReturn200WhenIssuingValidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.issue(id)).thenReturn(sampleResponse(id, InvoiceStatus.ISSUED));

        mockMvc.perform(post("/api/invoices/" + id + "/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void shouldReturn409WhenIssuingBlockedTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.issue(id)).thenThrow(new InvalidStatusTransitionException(InvoiceStatus.CANCELLED, "ser emitida"));

        mockMvc.perform(post("/api/invoices/" + id + "/issue"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shouldReturn200WhenCancellingValidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.cancel(id)).thenReturn(sampleResponse(id, InvoiceStatus.CANCELLED));

        mockMvc.perform(post("/api/invoices/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldReturn409WhenCancellingBlockedTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.cancel(id)).thenThrow(new InvalidStatusTransitionException(InvoiceStatus.ISSUED, "ser cancelada"));

        mockMvc.perform(post("/api/invoices/" + id + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}
