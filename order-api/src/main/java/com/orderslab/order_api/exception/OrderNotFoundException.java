package com.orderslab.order_api.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OrderNotFoundException(UUID orderId) {
        super("Pedido não encontrado: " + orderId);
    }
}
