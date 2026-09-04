package com.orderslab.payment_api.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(UUID paymentId) {
        super("Pagamento não encontrado: " + paymentId);
    }
}