---

description: "Task list — filtro por pedido no polling e listas limitadas (T2)"
---

# Tasks: Filtro por Pedido no Polling e Listas Limitadas

**Input**: Design documents from `/specs/021-filtro-polling-por-pedido/`

**Prerequisites**: plan.md, spec.md, research.md, contracts/list-endpoints.md

**Tests**: INCLUÍDOS — FR-009 exige testes de controller, service e persistência nos serviços e de hooks/componentes no
frontend.

**Organization**: por user story (US1 = consulta por pedido + tela de acompanhamento; US2 = listas limitadas com total;
US3 = suíte de carga).

## Format: `[ID] [P?] [Story] Description`

Caminhos relativos à raiz do repo. Cada serviço tem a **sua cópia** (Princípio I): faça no `payment-api` e porte para o
`invoice-api` trocando `Payment`→`Invoice`, `payments`→`invoices`, `payment_api`→`invoice_api`. Abreviações:
`<P>`/`<I>`/`<O>` = `payment-api`/`invoice-api`/`order-api` `/src/main/java/com/orderslab/<svc>_api`; `<PT>`/`<IT>`/`<OT>` =
os respectivos `src/test/java/...`. Maven em cada serviço (`./mvnw`). **Docker precisa estar ligado** (Testcontainers).

---

## Phase 1: Setup

- [X] T001 Rodar `./mvnw clean verify` em `order-api/`, `payment-api/`, `invoice-api/` e `npm ci && npm test` em
  `frontend-react/` como linha de base; registrar contagem de testes em `research.md` ("Validações empíricas").

---

## Phase 2: Foundational (bloqueia as user stories)

- [X] T002 [P] Criar `payment-api/src/main/resources/db/migration/V3__index_payments_order_id_created_at.sql`
  (`ix_payments_order_id` em `order_id`, `ix_payments_created_at` em `created_at DESC`) e o equivalente
  `invoice-api/.../V3__index_invoices_order_id_created_at.sql`.
- [X] T003 [P] Criar `order-api/src/main/resources/db/migration/V3__index_orders_created_at.sql` (`ix_orders_created_at`).
- [X] T004 [P] Em `<P>/exception/GlobalExceptionHandler.java`, `<I>/...` e `<O>/...`: tratar
  `MethodArgumentTypeMismatchException` (→ `"<param>: valor inválido"`) e `HandlerMethodValidationException`
  (→ `"<param>: <mensagem>"`) como 400 `ErrorResponse` "Erro de validação" com `validationErrors`.

**Checkpoint**: índices e validação de query param prontos.

---

## Phase 3: User Story 1 — Acompanhar custa o mesmo com 10 ou 100 mil pedidos (P1) 🎯 MVP

### Tests

- [X] T005 [P] [US1] `<PT>/controller/PaymentControllerTest.java` e `<IT>/...InvoiceControllerTest.java`:
  `?orderId=<uuid>` → 200 com a lista do service e sem `X-Total-Count`; `?orderId=abc` → 400 com
  `validationErrors[0]` começando por `orderId`; `orderId` + `limit` → usa o filtro por pedido.
- [X] T006 [P] [US1] `<PT>/service/PaymentServiceTest.java` e `<IT>/...`: `findByOrderId` consulta o repositório com
  `orderId.toString()` e mapeia para DTO.
- [X] T007 [US1] `<PT>/PaymentApiApplicationTests.java` e `<IT>/InvoiceApiApplicationTests.java` (Postgres real): dois
  pedidos com registros → `?orderId` devolve só os do pedido, mais recente primeiro; pedido sem registro → `[]`;
  índice `ix_*_order_id` existe em `pg_indexes`.
- [X] T008 [P] [US1] Frontend: `src/api/payments.test.ts` e `src/api/invoices.test.ts` (novos) conferindo a URL
  `?orderId=` (MSW); `src/components/FlowTracker.test.tsx` passando a mockar `listPaymentsByOrder`/`listInvoicesByOrder`
  e conferindo que são chamados com o id acompanhado.

### Implementation

- [X] T009 [US1] `<P>/repository/PaymentRepository.java` e `<I>/...`: `findByOrderIdOrderByCreatedAtDesc(String)`.
- [X] T010 [US1] `<P>/service/PaymentService.java` e `<I>/...`: `findByOrderId(UUID)` com log estruturado `debug`
  (`orderId`, `count`).
- [X] T011 [US1] `<P>/controller/PaymentController.java` e `<I>/...`: `@RequestParam(required = false) UUID orderId`
  no `GET`; com `orderId`, responde a lista filtrada.
- [X] T012 [US1] Frontend: `listPaymentsByOrder`/`listInvoicesByOrder` em `src/api/payments.ts`/`invoices.ts`;
  `src/hooks/useTrackedOrder.ts` passa a usá-las no `loadSnapshot`.

**Checkpoint**: acompanhamento consulta só o pedido; estados e parada automática inalterados.

---

## Phase 4: User Story 2 — Listagens não crescem sem limite (P2)

### Tests

- [X] T013 [P] [US2] Controller tests dos 3 serviços: sem parâmetro → `X-Total-Count` e service chamado com 200;
  `limit=5` → chamado com 5; `limit=0`/`501` → 400 com `validationErrors[0]` começando por `limit`; `limit=x` → 400.
- [X] T014 [P] [US2] Service tests dos 3 serviços: `findRecent(n)` pede `PageRequest` de tamanho n ordenado por
  `createdAt` desc e devolve itens + total (substitui os testes de `findAll`).
- [X] T015 [US2] `*ApplicationTests` dos 3 serviços (Postgres real): 3 registros, `?limit=2` → 2 mais recentes e
  `X-Total-Count: 3`; índice `ix_*_created_at` existe.
- [X] T016 [P] [US2] Frontend: `src/api/http.test.ts` (novo) para `requestList` (total do cabeçalho; fallback para o
  tamanho); `src/components/ServiceTable.test.tsx` mostrando o aviso de lista limitada com o total do servidor.

### Implementation

- [X] T017 [US2] `<P>/dto/PaymentPage.java`, `<I>/dto/InvoicePage.java`, `<O>/dto/OrderPage.java`:
  `record (List<…Response> items, long total)`.
- [X] T018 [US2] Services dos 3 serviços: `findAll()` → `findRecent(int limit)` via `findAll(PageRequest.of(0, limit,
  Sort.by(DESC, "createdAt")))`, com log `debug` (`limit`, `total`).
- [X] T019 [US2] Controllers dos 3 serviços: `@RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit`;
  sem `orderId`, responde `page.items()` com `X-Total-Count`.
- [X] T020 [US2] Frontend: `requestList` em `src/api/http.ts`; `listPayments`/`listInvoices`/`listOrders(limit)` →
  `{ items, total }`; `src/hooks/useServiceList.ts` e `src/components/ServiceTable.tsx` com `truncated` pelo total.

**Checkpoint**: listas devolvem no máximo o limite e a UI mostra o total real.

---

## Phase 5: User Story 3 — Teste de carga mede o sistema corrigido (P3)

- [X] T021 [US3] `load-tests/blackfriday.js`: `track()` usa `?orderId=`; `visitante()` usa `?limit=200`; métricas
  `lista_*_kb` passam a medir as respostas por pedido.
- [X] T022 [US3] Validar com `k6 inspect` e um smoke contra mock local que implemente o contrato novo.

---

## Phase 6: Polish

- [X] T023 Rodar `./mvnw clean verify` nos 3 serviços e `npm run lint`, `npm test`, `npm run build` no frontend;
  registrar resultados em `research.md`.
- [X] T024 [P] Atualizar `load-tests/README.md` (gargalo 1 resolvido) e o item T2 do `ROADMAP.md` com o resultado.

## Dependencies

- Phase 2 antes de US1/US2. US1 e US2 tocam os mesmos controllers/services: fazer US1 e depois US2 em cada serviço.
- Frontend T012 e T020 dependem só do contrato (podem rodar antes do backend, com MSW).
- US3 depende do contrato de US1/US2.
