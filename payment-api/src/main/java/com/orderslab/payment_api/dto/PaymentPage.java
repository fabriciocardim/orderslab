package com.orderslab.payment_api.dto;

import java.util.List;

/** Os registros mais recentes (até o limite pedido) e o total existente, enviado no cabeçalho X-Total-Count. */
public record PaymentPage(List<PaymentResponse> items, long total) {
}
