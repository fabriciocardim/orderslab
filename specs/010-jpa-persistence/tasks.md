# Tasks: Persistência JPA Simétrica

**Input**: Design documents from `/specs/010-jpa-persistence/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: testes de Service (item 1.6) adaptados para Mockito nesta feature; testes de
Controller (item 1.7) e integração (item 1.8) reaproveitados sem alteração de código — item
1.8 passa a exigir Postgres real rodando (ver FR-008 da spec).

## Fase 1: Setup — não aplicável

## Fase 2: Foundational

- [X] T001 Confirmar `postgres-api` rodando (`docker compose up -d postgres-api`, a partir
      de `infra/`) e os 3 bancos/usuários do item 1.9 acessíveis — pré-requisito de toda a
      fase seguinte

**Checkpoint**: Postgres real disponível em `localhost:5432` para os 3 serviços.

---

## Fase 3: User Story 1 - Dados sobrevivem a um restart do serviço (Priority: P1) 🎯 MVP

**Goal**: os 3 serviços persistem de verdade em PostgreSQL, com o mesmo padrão
(Entity+Repository+migration).

**Independent Test**: criar recurso via API, reiniciar o processo, buscar de novo — mesmos
dados (ver `quickstart.md`).

### Implementation for User Story 1 — order-api

- [X] T002 [P] [US1] `order-api`: wiring completo — descomentar `spring-data-jpa`+
      `postgresql` e adicionar `flyway-core`+`flyway-database-postgresql` em `pom.xml`;
      anotar `model/Order.java` como `@Entity` (`@Id`, `@Enumerated(EnumType.STRING)` em
      `status`, conforme `data-model.md`); criar `repository/OrderRepository.java`
      (`extends JpaRepository<Order, UUID>`); criar
      `src/main/resources/db/migration/V1__create_orders_table.sql`; ativar
      `spring.datasource.*`/`spring.jpa.*`/`spring.flyway.enabled=true` em
      `application.properties` (depende de T001)
- [X] T003 [US1] `order-api`: reescrever `service/OrderService.java` — `OrderRepository`
      injetado via construtor, `Map` removido, `findOrThrow`/`create`/`confirm`/`cancel`/
      `findAll` usando `save`/`findById`/`findAll` do Repository (depende de T002)
- [X] T004 [US1] `order-api`: adaptar
      `test/java/.../service/OrderServiceTest.java` — `@Mock OrderRepository` +
      `@InjectMocks OrderService` (Mockito), mantendo os mesmos 10 casos já cobertos pelo
      item 1.6 (depende de T003)
- [X] T005 [US1] `order-api`: rodar `./mvnw test` (com `postgres-api` no ar) — confirmar
      todos os testes (itens 1.6+1.7+1.8) passando com persistência real (depende de T004)

### Implementation for User Story 1 — payment-api

- [X] T006 [P] [US1] `payment-api`: mesmo wiring de T002, adaptado (`Payment.java`,
      `PaymentRepository.java`, `V1__create_payments_table.sql`) (depende de T001)
- [X] T007 [US1] `payment-api`: reescrever `service/PaymentService.java` (depende de T006)
- [X] T008 [US1] `payment-api`: adaptar `test/.../service/PaymentServiceTest.java` para
      Mockito (depende de T007)
- [X] T009 [US1] `payment-api`: rodar `./mvnw test` — confirmar tudo passando (depende de
      T008)

### Implementation for User Story 1 — invoice-api

- [X] T010 [P] [US1] `invoice-api`: mesmo wiring de T002, adaptado (`Invoice.java`,
      `InvoiceRepository.java`, `V1__create_invoices_table.sql`) (depende de T001)
- [X] T011 [US1] `invoice-api`: reescrever `service/InvoiceService.java` (depende de T010)
- [X] T012 [US1] `invoice-api`: adaptar `test/.../service/InvoiceServiceTest.java` para
      Mockito (depende de T011)
- [X] T013 [US1] `invoice-api`: rodar `./mvnw test` — confirmar tudo passando (depende de
      T012)

### Validação ponta a ponta

- [X] T014 [US1] Validar persistência real (`quickstart.md`) nos 3 serviços: criar recurso
      via API, reiniciar o processo, confirmar que os mesmos dados continuam lá (depende de
      T005, T009, T013)

**Checkpoint**: os 3 serviços persistem de verdade; toda a suíte de testes (1.6-1.8) passa
com Postgres real.

---

## Fase 4: User Story 2 - Schema do banco é versionado (Priority: P2)

**Goal**: confirmar que o schema é 100% governado pelas migrations, não pelo Hibernate.

**Independent Test**: banco vazio → serviço sobe → tabela criada automaticamente pela
migração.

### Implementation for User Story 2

- [X] T015 [US2] Confirmar, via `psql`, que `flyway_schema_history` em cada um dos 3 bancos
      lista a migração `V1__create_<tabela>_table` aplicada (depende de T014)
- [X] T016 [US2] Recriar o `postgres-api` do zero (`docker compose down postgres-api` +
      `up -d postgres-api` — sem volume nomeado, reinicializa limpo) e confirmar que, ao
      subir cada serviço, a migração recria a tabela automaticamente sem passo manual
      (depende de T015)

**Checkpoint**: schema 100% reproduzível a partir do código versionado.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T017 [P] Rodar `./mvnw clean verify` nos 3 serviços (com `postgres-api` no ar) —
      confirmar `BUILD SUCCESS` completo
- [X] T018 [P] Atualizar o item 1.10 de `ROADMAP.md` como concluído
- [ ] T019 Commitar as edições dos 3 serviços e `specs/010-jpa-persistence/` na branch
      `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001 (Foundational) bloqueia toda a Fase 3.
- Dentro de cada serviço: wiring → Service → Test → `mvnw test` (sequencial).
- Os 3 blocos de serviço (order/payment/invoice) são independentes entre si — o wiring
  (T002/T006/T010) é paralelizável; o restante de cada bloco é sequencial internamente.
- T014 depende dos 3 blocos completos. Fase 4 (US2) depende de T014. Polish depende de T016.

## Implementation Strategy

### MVP First (User Story 1)

1. T001 (Foundational).
2. Os 3 blocos de serviço (wiring → service → test → mvnw test) — podem ser feitos em
   sequência ou intercalados, já que são independentes entre si.
3. T014 — validar persistência real ponta a ponta.
4. **STOP and VALIDATE**: se os 3 serviços persistem de verdade, o MVP está pronto.

### Incremental Delivery

1. US1 (T002-T014) → persistência real funcionando, suíte de testes adaptada e passando.
2. US2 (T015-T016) → confirma que o schema é 100% reproduzível via migration.
3. Polish (T017-T019) → build completo, ROADMAP, commit.

## Notes

- Nenhuma mudança em `Controller`/`GlobalExceptionHandler`/DTOs — contrato HTTP idêntico.
- A partir desta feature, `./mvnw test`/CI exige um Postgres real alcançável para os testes
  de integração (item 1.8) passarem — lacuna aceita, resolvida pelo item 1.11
  (Testcontainers), próximo da fila.
