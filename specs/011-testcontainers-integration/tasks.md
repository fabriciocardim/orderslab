# Tasks: Testes de Integração com Testcontainers

**Input**: Design documents from `/specs/011-testcontainers-integration/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: esta feature é, ela própria, sobre infraestrutura de teste — não introduz testes
novos de produto, reescreve como os testes de integração HTTP (item 1.8) obtêm seu banco.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational

- [X] T001 Parar toda a infra do projeto (`cd infra && docker compose down`) — pré-requisito
      para validar de verdade que os testes passam a ser self-contained (FR-002); manter só
      o Docker Desktop/daemon rodando

**Checkpoint**: nenhuma infra do projeto no ar; só Docker disponível.

---

## Fase 3: User Story 1 - `mvnw test`/CI roda sem depender de infra externa (Priority: P1) 🎯 MVP

**Goal**: os 3 `*ApplicationTests.java` sobem seu próprio Postgres efêmero via
Testcontainers, sem exigir `docker compose up -d postgres-api` rodando antes.

**Independent Test**: numa máquina só com Docker rodando (infra do projeto parada), `mvnw
test` passa nos 3 serviços (ver `quickstart.md`).

### Implementation for User Story 1 — order-api

- [X] T002 [P] [US1] `order-api`: adicionar em escopo `test` no `pom.xml`:
      `spring-boot-testcontainers` (sem `<version>`), `testcontainers-postgresql`,
      `testcontainers-junit-jupiter` (nomes confirmados em `research.md` Decisão 1 — **não**
      `postgresql`/`junit-jupiter`, renomeados no Testcontainers 2.x)
- [X] T003 [US1] `order-api`: reescrever `OrderApiApplicationTests.java` — anotar a classe
      `@Testcontainers`; adicionar campo `@Container @ServiceConnection static
      PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")`
      (import `org.testcontainers.postgresql.PostgreSQLContainer`, pacote novo sem generics —
      `research.md` Decisão 3); manter os 3 cenários de teste HTTP já existentes (item 1.8)
      sem alteração de lógica (depende de T002)
- [X] T004 [US1] `order-api`: rodar `./mvnw test` com a infra do projeto parada (T001) —
      confirmar os 22 testes passando (10 Service + 9 Controller + 3 integração HTTP), com
      Testcontainers subindo o container `postgres:15-alpine` nos logs (depende de T003)

### Implementation for User Story 1 — payment-api

- [X] T005 [P] [US1] `payment-api`: mesmo wiring de T002 no `pom.xml` (depende de T001)
- [X] T006 [US1] `payment-api`: reescrever `PaymentApiApplicationTests.java`, mesmo padrão de
      T003 (depende de T005)
- [X] T007 [US1] `payment-api`: rodar `./mvnw test` com a infra do projeto parada — confirmar
      os 22 testes passando (depende de T006)

### Implementation for User Story 1 — invoice-api

- [X] T008 [P] [US1] `invoice-api`: mesmo wiring de T002 no `pom.xml` (depende de T001)
- [X] T009 [US1] `invoice-api`: reescrever `InvoiceApiApplicationTests.java`, mesmo padrão de
      T003 (depende de T008)
- [X] T010 [US1] `invoice-api`: rodar `./mvnw test` com a infra do projeto parada — confirmar
      os 22 testes passando (depende de T009)

### Validação cruzada

- [X] T011 [US1] Confirmar (`quickstart.md`) que `./mvnw test -Dtest=*ServiceTest,
      *ControllerTest` nos 3 serviços passa instantaneamente, sem qualquer log de
      Testcontainers/Docker — os itens 1.6/1.7 continuam sem depender de banco (FR-008)
      (depende de T004, T007, T010)

**Checkpoint**: os 3 serviços rodam `mvnw test` self-contained; 66/66 testes passando sem
infra do projeto previamente provisionada.

---

## Fase 4: User Story 2 - Schema de teste nasce da migração real, sem vazamento entre execuções (Priority: P2)

**Goal**: confirmar que o container efêmero usa a mesma migração Flyway de produção, e que
execuções sucessivas não interferem entre si.

**Independent Test**: rodar a suíte duas vezes seguidas na mesma máquina — resultado
idêntico nas duas.

### Implementation for User Story 2

- [X] T012 [US2] Nos logs de T004/T007/T010, confirmar a linha do Flyway aplicando a
      migração real (`Successfully applied 1 migration to schema "public"`) contra o
      container efêmero — não `ddl-auto`/H2/qualquer atalho exclusivo de teste (depende de
      T011)
- [X] T013 [US2] Rodar `./mvnw test` duas vezes seguidas em cada um dos 3 serviços e
      confirmar que as duas execuções passam de forma idêntica, sem qualquer interferência de
      dados entre rodadas (FR-007) (depende de T012)

**Checkpoint**: schema de teste 100% equivalente ao de produção; execuções isoladas entre si.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T014 [P] Rodar `./mvnw clean verify` nos 3 serviços com a infra do projeto ainda
      parada — confirmar `BUILD SUCCESS` totalmente self-contained
- [X] T015 [P] Atualizar o item 1.11 de `ROADMAP.md` como concluído
- [X] T016 Religar a infra do projeto (`cd infra && docker compose up -d`) para deixar o
      ambiente como estava antes desta feature, e commitar as edições dos 3 serviços e
      `specs/011-testcontainers-integration/` na branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001 (Foundational) bloqueia toda a Fase 3.
- Dentro de cada serviço: wiring do pom → reescrever `*ApplicationTests.java` → `mvnw test`
  (sequencial).
- Os 3 blocos de serviço são independentes entre si — o wiring do pom (T002/T005/T008) é
  paralelizável; o restante de cada bloco é sequencial internamente.
- T011 depende dos 3 blocos completos. Fase 4 (US2) depende de T011. Polish depende de T013.

## Implementation Strategy

### MVP First (User Story 1)

1. T001 (Foundational).
2. Os 3 blocos de serviço (pom → `*ApplicationTests.java` → `mvnw test`) — podem ser feitos
   em sequência ou intercalados, já que são independentes entre si.
3. T011 — confirmar que 1.6/1.7 continuam sem depender de Docker.
4. **STOP and VALIDATE**: se os 3 serviços rodam `mvnw test` self-contained, o MVP está
   pronto.

### Incremental Delivery

1. US1 (T002-T011) → `mvnw test`/CI self-contained nos 3 serviços.
2. US2 (T012-T013) → confirma paridade de schema com produção e isolamento entre execuções.
3. Polish (T014-T016) → build completo, ROADMAP, ambiente restaurado, commit.

## Notes

- Nenhuma mudança em `Controller`/`Service`/`Repository`/`application.properties`/migrations
  — contrato HTTP e comportamento de produção idênticos.
- A partir desta feature, `mvnw test`/CI não depende mais de nenhuma infra do projeto
  previamente provisionada — só de um runtime Docker disponível na máquina.
