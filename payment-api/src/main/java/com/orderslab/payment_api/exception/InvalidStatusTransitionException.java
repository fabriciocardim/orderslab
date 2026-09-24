package com.orderslab.payment_api.exception;

import com.orderslab.payment_api.model.PaymentStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidStatusTransitionException(PaymentStatus currentStatus, String attemptedAction) {
        super("Pagamento já está " + currentStatus + ", não pode " + attemptedAction + ".");
    }
}
