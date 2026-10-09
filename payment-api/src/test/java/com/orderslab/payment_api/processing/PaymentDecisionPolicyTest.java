package com.orderslab.payment_api.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.payment_api.model.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentDecisionPolicyTest {

    private final PaymentDecisionPolicy policy =
            new PaymentDecisionPolicy(new PaymentProperties(new BigDecimal("1000.00")));

    @Test
    void shouldReserveWhenBelowLimit() {
        PaymentDecisionPolicy.Decision decision = policy.decide(new BigDecimal("10.50"));

        assertThat(decision.status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void shouldReserveWhenExactlyAtLimitRegardlessOfScale() {
        assertThat(policy.decide(new BigDecimal("1000.00")).status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(policy.decide(new BigDecimal("1000")).status()).isEqualTo(PaymentStatus.RESERVED);
    }

    @Test
    void shouldFailWhenAboveLimit() {
        PaymentDecisionPolicy.Decision decision = policy.decide(new BigDecimal("1000.01"));

        assertThat(decision.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(decision.reason()).isEqualTo("AMOUNT_LIMIT_EXCEEDED");
    }

    @Test
    void shouldBeDeterministicAcrossCalls() {
        BigDecimal amount = new BigDecimal("1500");

        for (int i = 0; i < 20; i++) {
            assertThat(policy.decide(amount)).isEqualTo(policy.decide(amount));
        }
    }

    @Test
    void shouldHonorConfiguredLimit() {
        PaymentDecisionPolicy lower = new PaymentDecisionPolicy(new PaymentProperties(new BigDecimal("50")));

        assertThat(lower.decide(new BigDecimal("50.01")).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(lower.decide(new BigDecimal("50")).status()).isEqualTo(PaymentStatus.RESERVED);
    }
}
