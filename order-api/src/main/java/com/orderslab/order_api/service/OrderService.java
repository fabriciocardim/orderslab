package com.orderslab.order_api.service;

import com.orderslab.order_api.dto.OrderRequest;
import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.exception.InvalidStatusTransitionException;
import com.orderslab.order_api.exception.OrderNotFoundException;
import com.orderslab.order_api.model.Order;
import com.orderslab.order_api.model.OrderStatus;
import com.orderslab.order_api.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse create(OrderRequest request) {
        Order order = new Order(request.getCustomerId(), request.getAmount());
        orderRepository.save(order);
        log.atInfo()
                .addKeyValue("orderId", order.getId())
                .addKeyValue("status", order.getStatus())
                .log("Order created");
        return toResponse(order);
    }

    public OrderResponse confirm(UUID orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidStatusTransitionException(order.getStatus(), "ser confirmado");
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.atInfo()
                .addKeyValue("orderId", order.getId())
                .addKeyValue("status", order.getStatus())
                .log("Order confirmed");
        return toResponse(order);
    }

    public OrderResponse cancel(UUID orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidStatusTransitionException(order.getStatus(), "ser cancelado");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.atInfo()
                .addKeyValue("orderId", order.getId())
                .addKeyValue("status", order.getStatus())
                .log("Order cancelled");
        return toResponse(order);
    }

    public OrderResponse findById(UUID orderId) {
        return toResponse(findOrThrow(orderId));
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
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
