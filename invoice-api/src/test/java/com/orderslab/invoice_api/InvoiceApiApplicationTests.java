package com.orderslab.invoice_api;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderslab.invoice_api.dto.InvoiceResponse;
import com.orderslab.invoice_api.model.InvoiceStatus;
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
class InvoiceApiApplicationTests {

    private static final String ORDER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String PAYMENT_ID = "a8541a91-1fcc-4b2e-9fdc-aa23f090043a";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateIssueAndFindInvoiceThroughRealHttpFlow() {
        InvoiceResponse created = restTestClient.post().uri("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"paymentId\":\"" + PAYMENT_ID + "\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InvoiceResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(InvoiceStatus.PENDING);

        restTestClient.post().uri("/api/invoices/{id}/issue", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ISSUED");

        restTestClient.get().uri("/api/invoices/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ISSUED");
    }

    @Test
    void shouldCreateAndCancelInvoiceThroughRealHttpFlow() {
        InvoiceResponse created = restTestClient.post().uri("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"orderId\":\"" + ORDER_ID + "\",\"paymentId\":\"" + PAYMENT_ID + "\",\"amount\":20}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(InvoiceResponse.class)
                .returnResult()
                .getResponseBody();

        restTestClient.post().uri("/api/invoices/{id}/cancel", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }
}
