package com.orderslab.payment_api.processing;

import com.orderslab.payment_api.model.PaymentStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Regra simples e determinística: valor estritamente acima do limite falha; senão, reserva. */
@Component
public class PaymentDecisionPolicy {

    public static final String REASON_AMOUNT_LIMIT_EXCEEDED = "AMOUNT_LIMIT_EXCEEDED";

    private final PaymentProperties properties;

    public PaymentDecisionPolicy(PaymentProperties properties) {
        this.properties = properties;
    }

    public Decision decide(BigDecimal amount) {
        if (amount.compareTo(properties.limit()) > 0) {
            return new Decision(PaymentStatus.FAILED, REASON_AMOUNT_LIMIT_EXCEEDED);
        }
        return new Decision(PaymentStatus.RESERVED, null);
    }

    public record Decision(PaymentStatus status, String reason) {
    }
}
