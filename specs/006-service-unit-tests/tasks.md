# Tasks: Testes Unitários de Service

**Input**: Design documents from `/specs/006-service-unit-tests/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: esta feature É a suíte de testes — não há um segundo nível de teste sobre ela.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

---

## Fase 3: User Story 1 - Regressão de comportamento do Service é detectada automaticamente (Priority: P1) 🎯 MVP

**Goal**: cada `Service` tem uma suíte de testes cobrindo criação, busca, 404, transição
válida e bloqueada.

**Independent Test**: `./mvnw test` em cada serviço, novos testes passando.

### Implementation for User Story 1

- [X] T001 [P] [US1] Criar `order-api/src/test/java/com/orderslab/order_api/service/OrderServiceTest.java`
      cobrindo os 7 casos da matriz em `research.md` (criar, buscar, 404 em busca/ação,
      transição válida, transição bloqueada, lista vazia) para `confirm()` e `cancel()`
- [X] T002 [P] [US1] Criar `payment-api/.../service/PaymentServiceTest.java` com os mesmos
      7 casos, adaptados a `PaymentService` (estado inicial `RESERVED`)
- [X] T003 [P] [US1] Criar `invoice-api/.../service/InvoiceServiceTest.java` com os mesmos
      7 casos, adaptados a `InvoiceService` (ação `issue` em vez de uma das duas)
- [X] T004 [US1] Rodar `./mvnw test` nos 3 serviços; confirmar todos os testes novos
      passando (depende de T001, T002, T003)

**Checkpoint**: os 3 serviços têm cobertura de teste real do `Service`, todos os testes
passando.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T005 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar `BUILD SUCCESS`
      completo (não só `test`)
- [X] T006 [P] Atualizar o item 1.6 de `ROADMAP.md` como concluído
- [X] T007 Commitar as 3 classes de teste novas e `specs/006-service-unit-tests/` na branch
      `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001/T002/T003 são independentes entre si (arquivos diferentes, serviços diferentes) —
  paralelizáveis.
- T004 depende dos 3. Polish depende de T004.

## Implementation Strategy

### MVP First (única user story)

1. T001–T003 em paralelo.
2. T004 — confirmar tudo passando.
3. **STOP and VALIDATE**: se os 3 `./mvnw test` passam, a feature está pronta.

## Notes

- Nenhum arquivo em `src/main/` é alterado por esta feature.
