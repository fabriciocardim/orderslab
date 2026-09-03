package com.orderslab.order_api.service;

import com.orderslab.order_api.dto.OrderRequest;
import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.exception.OrderNotFoundException;
import com.orderslab.order_api.model.Order;
import com.orderslab.order_api.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OrderService {

    // Armazenamento em memória só para o exercício.
    // Depois pode virar um repository (JPA) apontando pro banco da Order API.
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    public OrderResponse create(OrderRequest request) {
        Order order = new Order(request.getCustomerId(), request.getAmount());
        orders.put(order.getId(), order);
        return toResponse(order);
    }

    public OrderResponse confirm(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.setStatus(OrderStatus.CONFIRMED);
        return toResponse(order);
    }

    public OrderResponse cancel(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(order);
    }

    public OrderResponse findById(UUID orderId) {
        return toResponse(findOrThrow(orderId));
    }

    public List<OrderResponse> findAll() {
        return orders.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Order findOrThrow(UUID orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
