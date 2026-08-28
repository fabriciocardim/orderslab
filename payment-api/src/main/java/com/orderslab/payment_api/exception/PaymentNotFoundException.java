package com.orderslab.payment_api.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID paymentId) {
        super("Pagamento não encontrado: " + paymentId);
    }
}