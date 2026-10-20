package com.orderslab.payment_api.controller;

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

import com.orderslab.payment_api.dto.PaymentPage;
import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.exception.InvalidStatusTransitionException;
import com.orderslab.payment_api.exception.PaymentNotFoundException;
import com.orderslab.payment_api.model.PaymentStatus;
import com.orderslab.payment_api.service.PaymentService;
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

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse(UUID id, PaymentStatus status) {
        return new PaymentResponse(id, ORDER_ID, BigDecimal.TEN, status, Instant.now(), Instant.now());
    }

    @Test
    void shouldReturn201WhenCreatingWithValidBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.reserve(any())).thenReturn(sampleResponse(id, PaymentStatus.RESERVED));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\",\"amount\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void shouldReturn400WhenCreatingWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"nao-e-um-uuid\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());

        verify(paymentService, never()).reserve(any());
    }

    @Test
    void shouldReturn200WhenFindingByIdExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.findById(id)).thenReturn(sampleResponse(id, PaymentStatus.RESERVED));

        mockMvc.perform(get("/api/payments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void shouldReturn404WhenFindingByIdDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.findById(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(get("/api/payments/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/payments/" + id));
    }

    @Test
    void shouldReturnTheMostRecentWithTotalCountWhenListingWithoutFilter() throws Exception {
        when(paymentService.findRecent(200)).thenReturn(new PaymentPage(List.of(sampleResponse(UUID.randomUUID(), PaymentStatus.RESERVED)), 3600));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3600"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldHonorTheRequestedLimit() throws Exception {
        when(paymentService.findRecent(5)).thenReturn(new PaymentPage(List.of(), 0));

        mockMvc.perform(get("/api/payments").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "501", "-1", "abc"})
    void shouldReturn400WhenLimitIsInvalid(String limit) throws Exception {
        mockMvc.perform(get("/api/payments").param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.path").value("/api/payments"))
                .andExpect(jsonPath("$.validationErrors[0]").value(startsWith("limit: ")));

        verify(paymentService, never()).findRecent(anyInt());
    }

    @Test
    void shouldReturnOnlyTheOrdersRecordsWhenFilteringByOrderId() throws Exception {
        when(paymentService.findByOrderId(UUID.fromString(ORDER_ID)))
                .thenReturn(List.of(sampleResponse(UUID.randomUUID(), PaymentStatus.RESERVED)));

        mockMvc.perform(get("/api/payments").param("orderId", ORDER_ID).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(ORDER_ID));

        verify(paymentService, never()).findRecent(anyInt());
    }

    @Test
    void shouldReturn400WhenOrderIdIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/payments").param("orderId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.validationErrors[0]").value("orderId: valor inválido"));

        verify(paymentService, never()).findByOrderId(any());
    }

    @Test
    void shouldReturn200WhenConfirmingValidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.confirm(id)).thenReturn(sampleResponse(id, PaymentStatus.CONFIRMED));

        mockMvc.perform(post("/api/payments/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldReturn409WhenConfirmingBlockedTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.confirm(id)).thenThrow(new InvalidStatusTransitionException(PaymentStatus.CANCELLED, "ser confirmado"));

        mockMvc.perform(post("/api/payments/" + id + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shouldReturn200WhenCancellingValidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.cancel(id)).thenReturn(sampleResponse(id, PaymentStatus.CANCELLED));

        mockMvc.perform(post("/api/payments/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldReturn409WhenCancellingBlockedTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.cancel(id)).thenThrow(new InvalidStatusTransitionException(PaymentStatus.CONFIRMED, "ser cancelado"));

        mockMvc.perform(post("/api/payments/" + id + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}
