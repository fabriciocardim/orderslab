package com.orderslab.invoice_api.exception;

import com.orderslab.invoice_api.model.InvoiceStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidStatusTransitionException(InvoiceStatus currentStatus, String attemptedAction) {
        super("Nota fiscal já está " + currentStatus + ", não pode " + attemptedAction + ".");
    }
}
