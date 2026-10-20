package com.orderslab.invoice_api.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderslab.invoice_api.dto.InvoicePage;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    void shouldReturnTheMostRecentWithTotalCountWhenListingWithoutFilter() throws Exception {
        when(invoiceService.findRecent(200)).thenReturn(new InvoicePage(List.of(sampleResponse(UUID.randomUUID(), InvoiceStatus.PENDING)), 3600));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3600"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldHonorTheRequestedLimit() throws Exception {
        when(invoiceService.findRecent(5)).thenReturn(new InvoicePage(List.of(), 0));

        mockMvc.perform(get("/api/invoices").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "501", "-1", "abc"})
    void shouldReturn400WhenLimitIsInvalid(String limit) throws Exception {
        mockMvc.perform(get("/api/invoices").param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.path").value("/api/invoices"))
                .andExpect(jsonPath("$.validationErrors[0]").value(startsWith("limit: ")));

        verify(invoiceService, never()).findRecent(anyInt());
    }

    @Test
    void shouldReturnOnlyTheOrdersRecordsWhenFilteringByOrderId() throws Exception {
        when(invoiceService.findByOrderId(UUID.fromString(ORDER_ID)))
                .thenReturn(List.of(sampleResponse(UUID.randomUUID(), InvoiceStatus.PENDING)));

        mockMvc.perform(get("/api/invoices").param("orderId", ORDER_ID).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(ORDER_ID));

        verify(invoiceService, never()).findRecent(anyInt());
    }

    @Test
    void shouldReturn400WhenOrderIdIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/invoices").param("orderId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.validationErrors[0]").value("orderId: valor inválido"));

        verify(invoiceService, never()).findByOrderId(any());
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
