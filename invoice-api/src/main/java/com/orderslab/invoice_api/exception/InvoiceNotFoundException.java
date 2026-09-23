package com.orderslab.invoice_api.exception;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvoiceNotFoundException(UUID invoiceId) {
        super("Nota fiscal não encontrada: " + invoiceId);
    }
}
