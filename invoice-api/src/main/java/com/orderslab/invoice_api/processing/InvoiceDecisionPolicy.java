package com.orderslab.invoice_api.processing;

import com.orderslab.invoice_api.model.InvoiceStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Regra simples e determinística (sem imposto real): valor estritamente acima do limite falha; senão, emite. */
@Component
public class InvoiceDecisionPolicy {

    public static final String REASON_AMOUNT_ABOVE_ISSUANCE_LIMIT = "AMOUNT_ABOVE_ISSUANCE_LIMIT";

    private final InvoiceProperties properties;

    public InvoiceDecisionPolicy(InvoiceProperties properties) {
        this.properties = properties;
    }

    public Decision decide(BigDecimal amount) {
        if (amount.compareTo(properties.limit()) > 0) {
            return new Decision(InvoiceStatus.FAILED, REASON_AMOUNT_ABOVE_ISSUANCE_LIMIT);
        }
        return new Decision(InvoiceStatus.ISSUED, null);
    }

    public record Decision(InvoiceStatus status, String reason) {
    }
}
