package com.orderslab.payment_api;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.payment_api.dto.PaymentResponse;
import com.orderslab.payment_api.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class PaymentApiApplicationTests {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReserveConfirmAndFindPaymentThroughRealHttpFlow() {
        PaymentResponse created = restTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.RESERVED);

        restTestClient.post().uri("/api/payments/{id}/confirm", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");

        restTestClient.get().uri("/api/payments/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void shouldReserveAndCancelPaymentThroughRealHttpFlow() {
        PaymentResponse created = restTestClient.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"amount\":20}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        restTestClient.post().uri("/api/payments/{id}/cancel", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }
}
