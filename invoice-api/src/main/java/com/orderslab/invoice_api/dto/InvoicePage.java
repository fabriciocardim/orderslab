package com.orderslab.invoice_api.dto;

import java.util.List;

/** Os registros mais recentes (até o limite pedido) e o total existente, enviado no cabeçalho X-Total-Count. */
public record InvoicePage(List<InvoiceResponse> items, long total) {
}
