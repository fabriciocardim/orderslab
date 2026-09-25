package com.orderslab.order_api;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.order_api.dto.OrderResponse;
import com.orderslab.order_api.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class OrderApiApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateConfirmAndFindOrderThroughRealHttpFlow() {
        OrderResponse created = restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cliente-1\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);

        restTestClient.post().uri("/api/orders/{id}/confirm", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");

        restTestClient.get().uri("/api/orders/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void shouldCreateAndCancelOrderThroughRealHttpFlow() {
        OrderResponse created = restTestClient.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cliente-2\",\"amount\":20}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        restTestClient.post().uri("/api/orders/{id}/cancel", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }
}
