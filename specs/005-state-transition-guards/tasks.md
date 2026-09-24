# Tasks: Máquina de Estados / Guardas de Transição

**Input**: Design documents from `/specs/005-state-transition-guards/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Validação empírica via boot + `curl` (mesmo padrão dos itens 1.3/1.4); testes
automatizados formais ficam para os itens 1.6/1.7 do ROADMAP.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational (bloqueia US1)

- [X] T001 [P] Criar `order-api/src/main/java/com/orderslab/order_api/exception/InvalidStatusTransitionException.java`
      (`RuntimeException`; construtor recebendo `OrderStatus` atual + ação tentada — ver
      `data-model.md`)
- [X] T002 [P] Criar `payment-api/.../exception/InvalidStatusTransitionException.java`
      (mesma estrutura, `PaymentStatus`)
- [X] T003 [P] Criar `invoice-api/.../exception/InvalidStatusTransitionException.java`
      (mesma estrutura, `InvoiceStatus`)

**Checkpoint**: exceção de transição inválida pronta nos 3 serviços.

---

## Fase 3: User Story 1 - Transições ilegais são bloqueadas (Priority: P1) 🎯 MVP

**Goal**: `confirm()`/`cancel()`/`issue()` só têm sucesso a partir do estado inicial; caso
contrário, `409`.

**Independent Test**: criar recurso, aplicar 1 transição válida, tentar uma 2ª — observar
`409`.

### Implementation for User Story 1

- [X] T004 [P] [US1] Em `order-api/.../service/OrderService.java`, adicionar guarda em
      `confirm()`/`cancel()`: MUST lançar `InvalidStatusTransitionException` quando
      `order.getStatus() != OrderStatus.PENDING` (depende de T001)
- [X] T005 [US1] Em `order-api/.../exception/GlobalExceptionHandler.java`, adicionar
      `@ExceptionHandler(InvalidStatusTransitionException.class)` retornando 409 com
      `ErrorResponse` (depende de T001)
- [X] T006 [P] [US1] Em `payment-api/.../service/PaymentService.java`, adicionar guarda em
      `confirm()`/`cancel()`: MUST lançar a exceção quando
      `payment.getStatus() != PaymentStatus.RESERVED` (depende de T002)
- [X] T007 [US1] Em `payment-api/.../exception/GlobalExceptionHandler.java`, adicionar o
      mesmo handler de T005 (depende de T002)
- [X] T008 [P] [US1] Em `invoice-api/.../service/InvoiceService.java`, adicionar guarda em
      `issue()`/`cancel()`: MUST lançar a exceção quando
      `invoice.getStatus() != InvoiceStatus.PENDING` (depende de T003)
- [X] T009 [US1] Em `invoice-api/.../exception/GlobalExceptionHandler.java`, adicionar o
      mesmo handler de T005 (depende de T003)
- [X] T010 [US1] Subir os 3 serviços; para cada um, criar um recurso, aplicar 1 transição
      válida (confirma sucesso, sem regressão) e tentar uma 2ª transição sobre o mesmo
      recurso (confirma `409`) (depende de T004, T005, T006, T007, T008, T009)

**Checkpoint**: os 3 serviços bloqueiam reuso de transição com 409; 1ª transição continua OK.

---

## Fase 4: User Story 2 - Estados sem uso real são removidos (Priority: P2)

**Goal**: enums só contêm valores alcançáveis de verdade.

**Independent Test**: inspecionar os 3 enums; confirmar payment recém-criado já vem
`RESERVED`.

### Implementation for User Story 2

- [X] T011 [P] [US2] Remover `COMPLETED` de
      `order-api/src/main/java/com/orderslab/order_api/model/OrderStatus.java`
- [X] T012 [P] [US2] Remover `PENDING` e `FAILED` de
      `payment-api/.../model/PaymentStatus.java`; ajustar
      `payment-api/.../model/Payment.java` (construtor) para setar `status = RESERVED`
      diretamente; remover a chamada redundante `setStatus(RESERVED)` em
      `PaymentService.reserve()`
- [X] T013 [P] [US2] Remover `FAILED` de
      `invoice-api/.../model/InvoiceStatus.java`
- [X] T014 [US2] `curl POST /api/payments` com dados válidos; confirmar que o status
      retornado já é `RESERVED` (depende de T012)

**Checkpoint**: nenhum dos 4 valores mortos resta nos 3 enums; `payment-api` cria direto
como `RESERVED`.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T015 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar `BUILD SUCCESS` e
      não-regressão da suíte existente
- [X] T016 [P] Atualizar o item 1.5 de `ROADMAP.md` como concluído
- [X] T017 Commitar as edições nos 3 serviços e `specs/005-state-transition-guards/` na
      branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001–T003 (Foundational) paralelizáveis entre si.
- Dentro de cada serviço, T00X(guarda)/T00Y(handler) dependem só da exceção do próprio
  serviço (T001/T002/T003) — os 3 pares serviço a serviço são independentes entre si.
- T010 depende de todos os pares US1 completos.
- T011/T012/T013 (US2) são edições de arquivo totalmente independentes de US1 (tocam
  enums/model, não o fluxo de guarda em si) — podem rodar em paralelo com US1.
- T014 depende de T012. Polish depende de US1+US2 completos.

## Implementation Strategy

### MVP First (User Story 1)

1. T001–T003 (Foundational) em paralelo.
2. T004–T009 (3 pares serviço a serviço) em paralelo.
3. T010 — validar 409/sucesso nos 3 serviços.
4. **STOP and VALIDATE**: se as guardas funcionam, o MVP está pronto.

### Incremental Delivery

1. Foundational → US1 (T004–T010) → transições ilegais bloqueadas.
2. US2 (T011–T014) → enums limpos, `payment-api` cria direto como `RESERVED`.
3. Polish (T015–T017) → build, ROADMAP, commit.

## Notes

- `PaymentStatus.PENDING`/`FAILED`, `InvoiceStatus.FAILED` e `OrderStatus.COMPLETED` somem
  do código — se algum teste futuro (itens 1.6/1.7) referenciá-los, isso indicaria um erro
  de planejamento, não um valor a restaurar.
