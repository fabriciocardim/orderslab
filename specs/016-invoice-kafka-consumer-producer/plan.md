# Implementation Plan: invoice-api como Consumidor e Produtor Kafka

**Branch**: `016-invoice-kafka-consumer-producer` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-invoice-kafka-consumer-producer/spec.md`

## Summary

O `invoice-api` passa a consumir `PaymentReserved` (`payment.reserved`) e, para cada pedido novo,
decidir a nota por regra determinística de valor (limite configurável, padrão 500.00),
publicando `InvoiceIssued` (`invoice.issued`) ou `InvoiceFailed` (`invoice.failed`).
`PaymentFailed` não é consumido. É o espelho do E2.3: cada mensagem é uma única transação (dedup +
nota + evento no outbox próprio), um relay publica depois, a idempotência vem de constraints
únicas em `invoices`, falha transitória é repetida sem descarte e mensagem ilegível é logada e
pulada. Decisões herdadas e deltas em [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 (`java.version` do `pom.xml`), Spring Boot 4.1.1

**Primary Dependencies**: `spring-boot-starter-kafka`, `spring-boot-starter-data-jpa`, Flyway,
Jackson 3 — **nenhuma dependência nova** no `pom.xml`

**Storage**: PostgreSQL (`invoice_db`) — migração Flyway `V2` (coluna + índices em `invoices` e
tabela `outbox_events`)

**Testing**: JUnit 5 + Mockito + Testcontainers Postgres (padrão do item 1.11); `KafkaTemplate`
mockado e processamento chamado diretamente; Kafka real no [quickstart](./quickstart.md)

**Target Platform**: contêiner Linux (Docker Compose local; k8s local com `replicas: 3`)

**Project Type**: microsserviço web (REST + mensageria) — apenas `invoice-api` é alterado

**Performance Goals**: resultado da nota no tópico em até 15 s após criar o pedido (SC-001);
relay com intervalo 1 s e lote 100

**Constraints**: contrato HTTP inalterado (FR-012), sem dependência de código entre serviços
(FR-013), `./mvnw test` autossuficiente (FR-015), sem DLT/Testcontainers Kafka/propagação de trace
por header/mudança em `order-api` ou `payment-api` (FR-016)

**Scale/Scope**: 2 eventos de saída, 1 de entrada, 3 tópicos declarados, 1 migração, ~12 classes novas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — eventos declarados dentro do `invoice-api`; outbox/relay copiados, não compartilhados; nenhum `pom.xml`/código de `order-api` ou `payment-api` alterado. Contrato externo = documentos do E2.1/E2.3. |
| II. Funcionalidade técnica real | **OK** — consumo e publicação reais em Kafka real, com idempotência e garantia de entrega, validados empiricamente (incl. ponta a ponta). Regra de negócio propositalmente simples (limite de valor, sem imposto real). |
| III. Persistência real | **OK** — decisão, dedup e outbox em Postgres no schema do próprio `invoice-api`. |
| IV. Portabilidade | **OK** — Kafka genérico; tópicos via `NewTopic`; env var de bootstrap já em Compose e k8s; migração via Flyway nos dois. |
| V. Segurança | **N/A**. |
| VI. Observabilidade | **OK** — logs estruturados com `orderId`/`eventId`/`eventType` + `traceId` (observação só no listener); propagação por header fica fora (FR-016). |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **OK sem adiantamento** — E2.4 está na ordem do `ROADMAP.md`. |

**Pós-design**: reavaliado após `data-model.md`, `contracts/` e `quickstart.md` — sem violações;
Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/016-invoice-kafka-consumer-producer/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── invoice-events.md
├── checklists/requirements.md
└── tasks.md             # gerado por /speckit-tasks
```

### Source Code (repository root)

Somente `invoice-api/` (estrutura espelha o `payment-api` do E2.3):

```text
invoice-api/src/main/java/com/orderslab/invoice_api/
├── event/        PaymentReservedMessage, InvoiceEvent, InvoiceIssued, InvoiceFailed
├── consumer/     PaymentReservedListener
├── processing/   InvoiceDecisionPolicy, InvoiceProperties, PaymentReservedProcessor
├── outbox/       OutboxEvent, OutboxEventRepository, OutboxWriter, OutboxRelay, OutboxProperties
├── config/       KafkaTopicsConfig, KafkaConsumerConfig, SchedulingConfig
├── model/        Invoice (+ sourceEventId, fábrica fromEvent), InvoiceStatus (+ FAILED)
└── repository/   InvoiceRepository (+ existsBySourceEventId, existsByOrderIdAndSourceEventIdIsNotNull)
invoice-api/src/main/resources/
├── application.properties            # + consumer/producer/outbox/invoice.issuance.limit
└── db/migration/V2__invoice_event_support.sql
invoice-api/src/test/java/com/orderslab/invoice_api/
├── processing/InvoiceDecisionPolicyTest, event/InvoiceEventsContractTest,
├── consumer/PaymentReservedListenerTest, consumer/KafkaConsumerConfigTest,
├── outbox/OutboxWriterTest, outbox/OutboxRelayTest
└── InvoiceApiApplicationTests        # + cenários de processamento/idempotência
```

**Structure Decision**: pacotes novos dentro do projeto único `invoice-api`, no estilo do
`payment-api`. Fora de `invoice-api/`, só a documentação (`ROADMAP.md`) é tocada ao concluir.

## Complexity Tracking

Sem violações da constitution a justificar.
