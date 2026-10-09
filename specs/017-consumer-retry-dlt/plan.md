# Implementation Plan: Estratégia de Erro de Consumo (Retry Limitado e DLT)

**Branch**: `017-consumer-retry-dlt` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-consumer-retry-dlt/spec.md`

## Summary

Substitui, nos dois consumidores (`payment-api` em `order.created`, `invoice-api` em
`payment.reserved`), a política interina do E2.3/E2.4 por: retry **limitado** com backoff exponencial
para falha transitória, **dead-letter topic** `<tópico>.dlt` ao esgotar as tentativas, e envio
**direto** ao DLT de mensagem permanentemente inválida (em vez de log e descarte). O DLT preserva chave,
valor e contexto de diagnóstico; se o envio ao DLT falhar, a mensagem é reentregue. Tudo via
`DefaultErrorHandler` + `DeadLetterPublishingRecoverer` do spring-kafka — sem tabela nova, sem
dependência nova. Decisões e evidências em [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1 (`spring-kafka` 4.1.1)

**Primary Dependencies**: `spring-boot-starter-kafka` — **nenhuma dependência nova** em nenhum `pom.xml`

**Storage**: nenhuma mudança de esquema (sem migração); só um ajuste de pool
(`spring.datasource.hikari.connection-timeout=5000`) para que a falha de banco seja rápida

**Testing**: JUnit 5 + Mockito; handler real exercitado via `handleOne` com `Consumer`/container/
`KafkaTemplate` mockados; Kafka/Postgres reais no [quickstart](./quickstart.md)

**Target Platform**: contêiner Linux (Compose local; k8s local com `replicas: 3`)

**Project Type**: microsserviços web — `payment-api` e `invoice-api` alterados (cada um com a sua cópia)

**Performance Goals**: mensagem com falha total do banco chega ao DLT em ≈ 40 s (limite configurável)

**Constraints**: garantias do E2.3/E2.4 intactas (FR-011); sem dependência de código entre serviços
(FR-012); sem `order-api`, Testcontainers Kafka, propagação de trace por header, endpoint/UI de DLT
(FR-015)

**Scale/Scope**: 2 serviços × (1 exceção, 1 properties, 1 config reescrita, 1 `NewTopic`, ajuste do
listener); 1 acréscimo ao contrato do E2.1

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — cada serviço tem a sua cópia (config, exceção, properties, DLT); nada compartilhado; `order-api` intocado. |
| II. Funcionalidade técnica real | **OK** — retry e DLT reais, validados empiricamente contra Kafka/Postgres reais; nenhuma mensagem some. |
| III. Persistência real | **OK** — sem mudança de esquema; idempotência existente garante o reprocessamento. |
| IV. Portabilidade | **OK** — Kafka genérico; DLT declarado via `NewTopic`; nenhuma variável nova obrigatória em Compose/k8s (padrões em `application.properties`, sobrepostos por env var se preciso). |
| V. Segurança | **N/A**. |
| VI. Observabilidade | **OK** — cada retry/DLT logado com origem, tentativa, causa e `traceId`; `orderId`/`eventId` quando legíveis. |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **OK sem adiantamento** — E2.5 está na ordem do `ROADMAP.md`. |

**Pós-design**: reavaliado após `data-model.md`, `contracts/` e `quickstart.md` — sem violações;
Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/017-consumer-retry-dlt/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── dlt-contract.md
├── checklists/requirements.md
└── tasks.md             # gerado por /speckit-tasks
```

### Source Code (repository root)

Em **cada** um de `payment-api/` e `invoice-api/` (mesma estrutura; nomes entre parênteses = invoice):

```text
<serviço>/src/main/java/com/orderslab/<pkg>/
├── consumer/
│   ├── InvalidMessageException.java          # NOVO: falha permanente (não-retentável)
│   └── OrderCreatedListener.java             # (PaymentReservedListener) lança em vez de log+return
├── config/
│   ├── ConsumerRetryProperties.java          # NOVO: consumer.retry.* (4 / 1000 / 2.0 / 10000)
│   ├── KafkaConsumerConfig.java              # REESCRITO: handler limitado + DLT + RetryListener
│   └── KafkaTopicsConfig.java                # + NewTopic <tópico>.dlt (3p/1r)
└── resources/application.properties          # + consumer.retry.* e hikari.connection-timeout
<serviço>/src/test/java/com/orderslab/<pkg>/consumer/
├── OrderCreatedListenerTest.java             # (PaymentReservedListenerTest) espera InvalidMessageException
└── KafkaConsumerConfigTest.java              # REESCRITO: handler real via handleOne
specs/013-kafka-event-convention/contracts/event-contract.md   # + convenção do DLT
ROADMAP.md                                                       # E2.5 concluído
```

**Structure Decision**: sem pacotes novos; só os já existentes de cada consumidor. Fora dos dois
serviços, tocam-se apenas a documentação (contrato do E2.1 e `ROADMAP.md`).

## Complexity Tracking

Sem violações da constitution a justificar.
