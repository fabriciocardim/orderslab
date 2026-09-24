package com.orderslab.payment_api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void shouldReturn200WhenListingResources() throws Exception {
        when(paymentService.findAll()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), PaymentStatus.RESERVED)));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
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
