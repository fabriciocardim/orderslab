# Implementation Plan: payment-api como Consumidor e Produtor Kafka

**Branch**: `015-payment-kafka-consumer-producer` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-payment-kafka-consumer-producer/spec.md`

## Summary

O `payment-api` passa a consumir `OrderCreated` (`order.created`) e, para cada pedido novo,
decidir o pagamento por uma regra determinística de valor (limite configurável, padrão
1000.00), publicando `PaymentReserved` (`payment.reserved`) ou `PaymentFailed`
(`payment.failed`). O processamento de cada mensagem é **uma única transação** que checa
duplicata, grava o pagamento e grava o evento de saída no **outbox** próprio; um relay
`@Scheduled` publica depois (mesmo desenho validado no E2.2). A idempotência vem de constraints
únicas em `payments` (`source_event_id` e `order_id` parcial). O erro de consumo transitório é
repetido sem descartar; a mensagem ilegível é logada e pulada. Decisões e evidências em
[research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 (`java.version` do `pom.xml`), Spring Boot 4.1.1

**Primary Dependencies**: `spring-boot-starter-kafka` (spring-kafka 4.1.1, kafka-clients 4.2.1),
`spring-boot-starter-data-jpa` (Hibernate 7), Flyway, Jackson 3 (`tools.jackson.*`).
**Nenhuma dependência nova** no `pom.xml`.

**Storage**: PostgreSQL (`payment_db`) — migração Flyway `V2` (coluna + índices em `payments`
e tabela `outbox_events`)

**Testing**: JUnit 5 + Mockito + Testcontainers Postgres (padrão do item 1.11); `KafkaTemplate`
mockado e processamento chamado diretamente; Kafka real validado no
[quickstart](./quickstart.md)

**Target Platform**: contêiner Linux (Docker Compose local; k8s local com `replicas: 3`)

**Project Type**: microsserviço web (REST + mensageria) — apenas `payment-api` é alterado

**Performance Goals**: resultado de pagamento no tópico em até 10 s após criar o pedido
(SC-001); relay com intervalo 1 s e lote 100

**Constraints**: contrato HTTP inalterado (FR-012), sem dependência de código entre serviços
(FR-013), `./mvnw test` autossuficiente (FR-015), sem `invoice-api`/DLT/Testcontainers
Kafka/propagação de trace por header/mudança no `order-api` (FR-016)

**Scale/Scope**: 2 eventos de saída, 1 de entrada, 3 tópicos declarados, 1 migração, ~12
classes novas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — eventos de entrada/saída declarados dentro do `payment-api`; outbox/relay copiados (não compartilhados); nenhum `pom.xml`/código de `order-api` ou `invoice-api` alterado. Contrato externo é o documento do E2.1/E2.2. |
| II. Funcionalidade técnica real | **OK** — consumo e publicação reais em Kafka real, com idempotência e garantia de entrega, validados empiricamente. Regra de negócio propositalmente simples (limite de valor). |
| III. Persistência real | **OK** — decisão, deduplicação e outbox em Postgres no schema do próprio `payment-api`; sem acesso a tabelas de outros serviços. |
| IV. Portabilidade | **OK** — Kafka genérico; tópicos declarados via `NewTopic`; env var de bootstrap já presente em Compose e k8s; migração via Flyway igual nos dois. |
| V. Segurança | **N/A** — camada de segurança ainda não introduzida. |
| VI. Observabilidade | **OK** — logs estruturados em consumo e publicação com `orderId`/`eventId`/`eventType` + `traceId`; observação ligada só no listener; propagação por header Kafka fica fora (FR-016). |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **OK sem adiantamento** — E2.3 está na ordem do `ROADMAP.md`. |

**Pós-design**: reavaliado após `data-model.md`, `contracts/` e `quickstart.md` — sem
violações; Complexity Tracking vazio. A duplicação de código de outbox entre serviços é
consequência direta do Princípio I, não um desvio.

## Project Structure

### Documentation (this feature)

```text
specs/015-payment-kafka-consumer-producer/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── payment-events.md
├── checklists/requirements.md
└── tasks.md             # gerado por /speckit-tasks
```

### Source Code (repository root)

Somente `payment-api/`:

```text
payment-api/
├── pom.xml                                   # sem alteração
└── src/
    ├── main/
    │   ├── java/com/orderslab/payment_api/
    │   │   ├── event/
    │   │   │   ├── OrderCreatedMessage.java  # entrada (só os campos usados; ignora desconhecidos)
    │   │   │   ├── PaymentEvent.java         # envelope (interface)
    │   │   │   ├── PaymentReserved.java      # + paymentId, amount
    │   │   │   └── PaymentFailed.java        # + reason
    │   │   ├── consumer/
    │   │   │   └── OrderCreatedListener.java # @KafkaListener; parse; descarta ilegível
    │   │   ├── processing/
    │   │   │   ├── PaymentDecisionPolicy.java # regra pura (limite configurável)
    │   │   │   ├── PaymentProperties.java     # payment.approval.limit
    │   │   │   └── OrderCreatedProcessor.java # @Transactional: dedup + pagamento + outbox
    │   │   ├── outbox/
    │   │   │   ├── OutboxEvent.java
    │   │   │   ├── OutboxEventRepository.java
    │   │   │   ├── OutboxWriter.java         # MANDATORY
    │   │   │   ├── OutboxRelay.java          # @Scheduled, FOR UPDATE, ack, remove
    │   │   │   └── OutboxProperties.java
    │   │   ├── config/
    │   │   │   ├── KafkaTopicsConfig.java    # NewTopic: order.created, payment.reserved/failed
    │   │   │   ├── KafkaConsumerConfig.java  # DefaultErrorHandler (retry ilimitado)
    │   │   │   └── SchedulingConfig.java     # @EnableScheduling + properties
    │   │   ├── model/Payment.java            # + sourceEventId, fábrica a partir de evento
    │   │   ├── model/PaymentStatus.java      # + FAILED
    │   │   └── repository/PaymentRepository.java  # + existsBySourceEventId, existsByOrderId...
    │   └── resources/
    │       ├── application.properties        # + consumer/producer/outbox/payment.*
    │       └── db/migration/V2__payment_event_support.sql
    └── test/java/com/orderslab/payment_api/
        ├── processing/PaymentDecisionPolicyTest.java
        ├── event/PaymentEventsContractTest.java
        ├── consumer/OrderCreatedListenerTest.java
        ├── outbox/OutboxWriterTest.java
        ├── outbox/OutboxRelayTest.java
        └── PaymentApiApplicationTests.java   # + cenários de processamento/idempotência
```

**Structure Decision**: pacotes novos `event/`, `consumer/`, `processing/`, `outbox/` e
`config/` dentro do projeto único `payment-api`, no mesmo estilo por camada do `order-api`.
Fora de `payment-api/`, só a documentação (`ROADMAP.md`) é tocada ao concluir.

## Complexity Tracking

Sem violações da constitution a justificar.
