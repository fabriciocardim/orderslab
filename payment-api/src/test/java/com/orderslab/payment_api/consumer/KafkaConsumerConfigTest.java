package com.orderslab.payment_api.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.payment_api.config.KafkaConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

class KafkaConsumerConfigTest {

    @Test
    void transientFailuresShouldBeRetriedForeverWithOneSecondBackoff() {
        FixedBackOff backOff = KafkaConsumerConfig.transientFailureBackOff();

        assertThat(backOff.getInterval()).isEqualTo(1000L);
        assertThat(backOff.getMaxAttempts()).isEqualTo(FixedBackOff.UNLIMITED_ATTEMPTS);
    }

    @Test
    void errorHandlerBeanShouldBeTheDefaultErrorHandlerSoBootAppliesIt() {
        assertThat(new KafkaConsumerConfig().kafkaErrorHandler()).isInstanceOf(DefaultErrorHandler.class);
    }
}
