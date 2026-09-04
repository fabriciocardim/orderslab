package com.orderslab.invoice_api.exception;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(UUID invoiceId) {
        super("Nota fiscal não encontrada: " + invoiceId);
    }
}
