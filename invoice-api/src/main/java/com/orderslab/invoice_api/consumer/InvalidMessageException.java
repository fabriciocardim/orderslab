package com.orderslab.invoice_api.consumer;

/**
 * Conteúdo da mensagem de entrada é inválido (ilegível, campo obrigatório ausente, valor nulo).
 * Falha permanente: não adianta repetir; o handler a envia direto ao dead-letter topic.
 */
public class InvalidMessageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
