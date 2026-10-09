package com.orderslab.invoice_api.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.invoice_api.model.InvoiceStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceDecisionPolicyTest {

    private final InvoiceDecisionPolicy policy =
            new InvoiceDecisionPolicy(new InvoiceProperties(new BigDecimal("500.00")));

    @Test
    void shouldIssueWhenBelowLimit() {
        InvoiceDecisionPolicy.Decision decision = policy.decide(new BigDecimal("10.50"));

        assertThat(decision.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void shouldIssueWhenExactlyAtLimitRegardlessOfScale() {
        assertThat(policy.decide(new BigDecimal("500.00")).status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(policy.decide(new BigDecimal("500")).status()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void shouldFailWhenAboveLimit() {
        InvoiceDecisionPolicy.Decision decision = policy.decide(new BigDecimal("500.01"));

        assertThat(decision.status()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(decision.reason()).isEqualTo("AMOUNT_ABOVE_ISSUANCE_LIMIT");
    }

    @Test
    void shouldBeDeterministicAcrossCalls() {
        BigDecimal amount = new BigDecimal("750");

        for (int i = 0; i < 20; i++) {
            assertThat(policy.decide(amount)).isEqualTo(policy.decide(amount));
        }
    }

    @Test
    void shouldHonorConfiguredLimit() {
        InvoiceDecisionPolicy lower = new InvoiceDecisionPolicy(new InvoiceProperties(new BigDecimal("50")));

        assertThat(lower.decide(new BigDecimal("50.01")).status()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(lower.decide(new BigDecimal("50")).status()).isEqualTo(InvoiceStatus.ISSUED);
    }
}
