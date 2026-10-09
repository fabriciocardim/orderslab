# Implementation Plan: order-api como Produtor Kafka

**Branch**: `014-order-kafka-producer` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-order-kafka-producer/spec.md`

## Summary

Cada transição persistida de pedido (`create`/`confirm`/`cancel`) passa a gerar o evento
`OrderCreated`/`OrderConfirmed`/`OrderCancelled` conforme o contrato do E2.1. A consistência
entre estado e evento (FR-005/FR-006) vem de um **outbox transacional**: o evento é gravado na
tabela `outbox_events` na mesma transação que altera o pedido, e um relay agendado o publica
no Kafka e o remove. A concorrência (FR-009) é resolvida com `SELECT ... FOR UPDATE` no pedido
em `confirm`/`cancel`. A ordem por pedido (FR-008) vem do `id` crescente do outbox + chave
Kafka `orderId` + envio sequencial com ack. Decisões e evidências em
[research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 (`java.version` do `pom.xml`), Spring Boot 4.1.1

**Primary Dependencies**: `spring-boot-starter-kafka` (spring-kafka 4.1.1, kafka-clients 4.2.1),
`spring-boot-starter-data-jpa` (Hibernate 7.4.5), Flyway, Jackson 3 (`tools.jackson.*`).
**Nenhuma dependência nova** no `pom.xml`.

**Storage**: PostgreSQL (`order_db`), nova tabela `outbox_events` via Flyway `V2`

**Testing**: JUnit 5 + Mockito + Testcontainers Postgres (padrão do item 1.11); `KafkaTemplate`
mockado nos testes automatizados; Kafka real validado no [quickstart](./quickstart.md)

**Target Platform**: contêiner Linux (Docker Compose local; k8s local com `replicas: 3`)

**Project Type**: microsserviço web (REST) — apenas `order-api` é alterado

**Performance Goals**: evento observável no tópico em até 5 s após a resposta da API (SC-001);
relay com intervalo de 1 s e lote de 100

**Constraints**: contrato HTTP inalterado (FR-011); sem dependência de código entre serviços
(FR-012); `./mvnw test` autossuficiente com só um runtime de contêiner (FR-014); sem
consumidores, DLT, Testcontainers Kafka ou propagação de trace por header (FR-015)

**Scale/Scope**: laboratório — 3 tipos de evento, 3 tópicos, 1 tabela, ~6 classes novas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — classes de evento declaradas dentro do `order-api`; nenhum `pom.xml`/código dos outros serviços alterado; contrato externo é o documento do E2.1 (FR-012). |
| II. Funcionalidade técnica real | **OK** — publicação real em Kafka real, com garantia de entrega (outbox), validado empiricamente. Request inválido continua rejeitado por Bean Validation sem gerar evento. |
| III. Persistência real | **OK** — outbox é tabela Postgres no schema do próprio `order-api`; sem acesso a tabelas de outros serviços. |
| IV. Portabilidade | **OK** — nenhum serviço proprietário; tópicos declarados via `NewTopic` (não dependem de auto-create do broker). Env vars de bootstrap já existem em Compose e k8s; a tabela nova vem por Flyway, igual nos dois. |
| V. Segurança (Keycloak) | **N/A** — Fase 5. |
| VI. Observabilidade | **OK** — logs estruturados no enqueue e no relay com `orderId`/`eventId`/`eventType` + `traceId` de origem (FR-010). Propagação por header Kafka fica para E6.4 (FR-015). |
| VII. Remediação autônoma | **N/A**. |
| Fases / ordem do ROADMAP | **OK sem adiantamento** — E2.2 é a Fase 2 do ROADMAP. (A constitution v3.3.0 deixou de descrever fases; a pendência de emenda anotada antes ficou superada.) |

**Pós-design**: reavaliado após `data-model.md`, `contracts/` e `quickstart.md` — sem violações;
Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/014-order-kafka-producer/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── order-events.md
├── checklists/requirements.md
└── tasks.md             # gerado por /speckit-tasks
```

### Source Code (repository root)

Somente `order-api/` (mais a nota de esclarecimento em
`specs/013-kafka-event-convention/contracts/event-contract.md`):

```text
order-api/
├── pom.xml                                  # sem alteração
└── src/
    ├── main/
    │   ├── java/com/orderslab/order_api/
    │   │   ├── event/
    │   │   │   ├── OrderEvent.java          # envelope (interface/record base)
    │   │   │   ├── OrderCreated.java        # + customerId, amount
    │   │   │   ├── OrderConfirmed.java
    │   │   │   └── OrderCancelled.java
    │   │   ├── outbox/
    │   │   │   ├── OutboxEvent.java         # @Entity outbox_events
    │   │   │   ├── OutboxEventRepository.java
    │   │   │   ├── OutboxWriter.java        # @Transactional(MANDATORY): serializa + grava
    │   │   │   ├── OutboxRelay.java         # @Scheduled: lê, publica com ack, remove
    │   │   │   └── OutboxProperties.java    # intervalo, lote, timeout de envio
    │   │   ├── config/
    │   │   │   └── KafkaTopicsConfig.java   # beans NewTopic (3 partições, 1 réplica)
    │   │   ├── repository/OrderRepository.java  # + findByIdForUpdate (PESSIMISTIC_WRITE)
    │   │   └── service/OrderService.java    # @Transactional + chama OutboxWriter
    │   └── resources/
    │       ├── application.properties       # + producer (acks, timeouts) + outbox.*
    │       └── db/migration/V2__create_outbox_events_table.sql
    └── test/java/com/orderslab/order_api/
        ├── event/OrderEventsContractTest.java
        ├── outbox/OutboxWriterTest.java
        ├── outbox/OutboxRelayTest.java
        ├── service/OrderServiceTest.java    # adaptado
        └── OrderApiApplicationTests.java    # + cenários de outbox/concorrência
```

**Structure Decision**: pacotes novos `event/`, `outbox/` e `config/` dentro do projeto único
`order-api`, seguindo a organização por camada já usada (`controller`, `service`,
`repository`...). `@EnableScheduling` vai na classe de aplicação ou na config do relay.
Nada fora de `order-api/` muda, exceto a nota no contrato do E2.1.

## Complexity Tracking

Sem violações da constitution a justificar.
