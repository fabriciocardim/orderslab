package com.orderslab.order_api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.exception.InvalidStatusTransitionException;
import com.orderslab.order_api.exception.OrderNotFoundException;
import com.orderslab.order_api.model.OrderStatus;
import com.orderslab.order_api.service.OrderService;
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

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse sampleResponse(UUID id, OrderStatus status) {
        return new OrderResponse(id, "cliente-1", BigDecimal.TEN, status, Instant.now(), Instant.now());
    }

    @Test
    void shouldReturn201WhenCreatingWithValidBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.create(any())).thenReturn(sampleResponse(id, OrderStatus.PENDING));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"cliente-1\",\"amount\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturn400WhenCreatingWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());

        verify(orderService, never()).create(any());
    }

    @Test
    void shouldReturn200WhenFindingByIdExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.findById(id)).thenReturn(sampleResponse(id, OrderStatus.PENDING));

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void shouldReturn404WhenFindingByIdDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.findById(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/orders/" + id));
    }

    @Test
    void shouldReturn200WhenListingResources() throws Exception {
        when(orderService.findAll()).thenReturn(List.of(sampleResponse(UUID.randomUUID(), OrderStatus.PENDING)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldReturn200WhenConfirmingValidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.confirm(id)).thenReturn(sampleResponse(id, OrderStatus.CONFIRMED));

        mockMvc.perform(post("/api/orders/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldReturn409WhenConfirmingBlockedTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.confirm(id)).thenThrow(new InvalidStatusTransitionException(OrderStatus.CANCELLED, "ser confirmado"));

        mockMvc.perform(post("/api/orders/" + id + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shouldReturn200WhenCancellingValidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancel(id)).thenReturn(sampleResponse(id, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldReturn409WhenCancellingBlockedTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancel(id)).thenThrow(new InvalidStatusTransitionException(OrderStatus.CONFIRMED, "ser cancelado"));

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}
