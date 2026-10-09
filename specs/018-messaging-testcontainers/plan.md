# Implementation Plan: Testes de Mensageria com Kafka Real (Testcontainers)

**Branch**: `018-messaging-testcontainers` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-messaging-testcontainers/spec.md`

## Summary

Cada serviço ganha uma suíte de mensageria automatizada que roda contra um **Kafka real** (imagem
`apache/kafka:4.2.0`, a do compose) e um Postgres real, ambos por Testcontainers, dentro de `./mvnw test`:
`order-api` verifica publicação (tópico, chave, envelope, partições, ordem, broker pausado), `payment-api` e
`invoice-api` verificam consumo+publicação, idempotência e DLT. Só código de teste, propriedades de teste e a
dependência de teste `testcontainers-kafka`. Decisões em [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1, Testcontainers 2.0.5

**Primary Dependencies**: acrescenta `org.testcontainers:testcontainers-kafka` (`test`, versão gerenciada pelo
BOM) a cada `pom.xml`; nada mais. `kafka-clients`, `spring-kafka-test` e `spring-boot-testcontainers` já existem.

**Storage**: Postgres (Testcontainers, como desde o 1.11) + Kafka (Testcontainers)

**Testing**: JUnit 5, `@SpringBootTest(RANDOM_PORT)` + `@Testcontainers` + `@ServiceConnection`; consumidor de
teste com `kafka-clients`; polling com prazo; sem pausas fixas como sincronização

**Target Platform**: máquina de desenvolvimento / CI com runtime de contêiner

**Project Type**: três microsserviços independentes — só `src/test` (e o `pom.xml` de teste) muda em cada um

**Performance Goals**: a suíte de mensageria adiciona ≤ ~2 min ao `verify` de cada serviço (SC-004)

**Constraints**: nenhuma mudança em `src/main` (FR-013); nenhum código de teste compartilhado entre serviços
(FR-012); suítes existentes intactas (FR-011); `./mvnw test` só exige runtime de contêiner (FR-014)

**Scale/Scope**: 3 classes de teste novas + 3 auxiliares de teste + 3 poms; ~14 testes no total

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — cada serviço tem a sua suíte e o seu auxiliar de teste (copiados, não compartilhados); poms independentes; nenhum teste atravessa serviços. |
| II. Funcionalidade técnica real | **OK** — é exatamente o objetivo: provar o fluxo contra Kafka real, de forma automatizada. |
| III. Persistência real | **OK** — Postgres real nos testes, como no item 1.11. |
| IV. Portabilidade | **OK** — a imagem de teste é a do `docker-compose`; nenhuma mudança de infraestrutura. |
| V. Segurança | **N/A**. |
| VI. Observabilidade | **OK** — sem mudança; os testes não removem nem alteram instrumentação. |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **OK sem adiantamento** — E2.6 está na ordem do `ROADMAP.md` e fecha a Fase 2. |
| Padrões de qualidade | **OK** — testes seguem o PMD do pipeline (`verify` + `pmd:pmd` limpos). |

**Pós-design**: reavaliado após `data-model.md` e `quickstart.md` — sem violações; Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/018-messaging-testcontainers/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── tasks.md             # gerado por /speckit-tasks
```

(Sem `contracts/`: a feature não expõe interface nova; os contratos testados são os dos itens E2.1–E2.5.)

### Source Code (repository root)

Somente `src/test` e `pom.xml` (escopo `test`) de cada serviço:

```text
order-api/
├── pom.xml                                                        # + testcontainers-kafka (test)
└── src/test/java/com/orderslab/order_api/messaging/
    ├── KafkaTestSupport.java            # consumidor de teste, polling com prazo, asserções
    └── OrderMessagingTest.java          # US1: publicação, partições, ordem, broker pausado
payment-api/
├── pom.xml                                                        # + testcontainers-kafka (test)
└── src/test/java/com/orderslab/payment_api/messaging/
    ├── KafkaTestSupport.java            # cópia própria do auxiliar
    └── PaymentMessagingTest.java        # US2: consumo+publicação, idempotência, DLT
invoice-api/
├── pom.xml                                                        # + testcontainers-kafka (test)
└── src/test/java/com/orderslab/invoice_api/messaging/
    ├── KafkaTestSupport.java            # cópia própria do auxiliar
    └── InvoiceMessagingTest.java        # US3: consumo+publicação, idempotência, DLT
```

**Structure Decision**: pacote `messaging` novo em `src/test` de cada serviço, separando visualmente os testes com
Kafka real das suítes com broker simulado. Fora de `src/test` e dos poms, só a documentação (`ROADMAP.md`) é tocada.

## Complexity Tracking

Sem violações da constitution a justificar.
