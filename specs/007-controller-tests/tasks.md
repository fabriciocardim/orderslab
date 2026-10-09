# Tasks: Testes de Controller (@WebMvcTest)

**Input**: Design documents from `/specs/007-controller-tests/`

**Prerequisites**: plan.md, spec.md, research.md

**Tests**: esta feature É a suíte de testes — não há um segundo nível de teste sobre ela.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

---

## Fase 3: User Story 1 - Contrato HTTP de cada API é validado automaticamente (Priority: P1) 🎯 MVP

**Goal**: cada `Controller` tem uma suíte `@WebMvcTest` cobrindo os 9 casos de
`research.md`.

**Independent Test**: `./mvnw test` em cada serviço, novos testes passando.

### Implementation for User Story 1

- [X] T001 [P] [US1] Criar `order-api/src/test/java/com/orderslab/order_api/controller/OrderControllerTest.java`
      (`@WebMvcTest(OrderController.class)` + `@MockitoBean OrderService`) cobrindo os 9
      casos da matriz em `research.md` para `create`/`findById`/`findAll`/`confirm`/`cancel`
- [X] T002 [P] [US1] Criar `payment-api/.../controller/PaymentControllerTest.java` com os
      mesmos 9 casos, adaptados a `PaymentController`/`PaymentService`
- [X] T003 [P] [US1] Criar `invoice-api/.../controller/InvoiceControllerTest.java` com os
      mesmos 9 casos, adaptados a `InvoiceController`/`InvoiceService` (`issue` em vez de
      uma das duas ações)
- [X] T004 [US1] Rodar `./mvnw test` nos 3 serviços; confirmar todos os testes (item 1.6 +
      esta feature) passando (depende de T001, T002, T003)

**Checkpoint**: os 3 serviços têm cobertura de teste de contrato HTTP completo.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T005 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar `BUILD SUCCESS`
      completo
- [X] T006 [P] Atualizar o item 1.7 de `ROADMAP.md` como concluído
- [X] T007 Commitar as 3 classes de teste novas e `specs/007-controller-tests/` na branch
      `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001/T002/T003 independentes entre si — paralelizáveis.
- T004 depende dos 3. Polish depende de T004.

## Implementation Strategy

### MVP First (única user story)

1. T001–T003 em paralelo.
2. T004 — confirmar tudo passando (item 1.6 + item 1.7 juntos).
3. **STOP and VALIDATE**: se os 3 `./mvnw test` passam, a feature está pronta.

## Notes

- Nenhum arquivo em `src/main/` é alterado por esta feature.
- Se algum pacote/classe confirmado em `research.md` não compilar como esperado, é sinal de
  mais uma diferença do Spring Boot 4.1.1 não capturada na pesquisa — investigar via
  mensagem de erro do compilador antes de tentar alternativas às cegas.
