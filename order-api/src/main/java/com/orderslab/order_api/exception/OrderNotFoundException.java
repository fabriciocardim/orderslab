package com.orderslab.order_api.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Pedido não encontrado: " + orderId);
    }
}
