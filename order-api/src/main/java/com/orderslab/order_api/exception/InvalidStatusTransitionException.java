package com.orderslab.order_api.exception;

import com.orderslab.order_api.model.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidStatusTransitionException(OrderStatus currentStatus, String attemptedAction) {
        super("Pedido já está " + currentStatus + ", não pode " + attemptedAction + ".");
    }
}
