# Tasks: Tratamento de Erro HTTP — 404 Real

**Input**: Design documents from `/specs/003-http-404-error-handling/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Não incluídos formalmente nesta feature — testes automatizados de controller
(`@WebMvcTest`) já são um item próprio e posterior no ROADMAP (1.7). Esta feature valida o
comportamento empiricamente (subir o serviço + `curl`), conforme `quickstart.md`.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

Cada `ErrorResponse`/`GlobalExceptionHandler` é local ao seu próprio serviço — não há
infraestrutura compartilhada a preparar antes (Princípio I).

---

## Fase 3: User Story 1 - Status HTTP correto para recurso inexistente (Priority: P1) 🎯 MVP

**Goal**: Os 3 serviços retornam `404` (não `500`) para id inexistente.

**Independent Test**: `curl` num id inexistente de cada serviço e observar status `404`.

### Implementation for User Story 1

- [X] T001 [P] [US1] Criar `order-api/src/main/java/com/orderslab/order_api/exception/ErrorResponse.java`
      (record: `timestamp`, `status`, `error`, `message`, `path` — ver `data-model.md`)
- [X] T002 [US1] Criar `order-api/src/main/java/com/orderslab/order_api/exception/GlobalExceptionHandler.java`
      (`@RestControllerAdvice`; `@ExceptionHandler(OrderNotFoundException.class)` retornando
      `ResponseEntity<ErrorResponse>` com status 404, `error="Not Found"`, `message` da
      exceção, `path` via `HttpServletRequest#getRequestURI()`) (depende de T001)
- [X] T003 [P] [US1] Criar `payment-api/src/main/java/com/orderslab/payment_api/exception/ErrorResponse.java`
      (mesma estrutura de T001, independente)
- [X] T004 [US1] Criar `payment-api/src/main/java/com/orderslab/payment_api/exception/GlobalExceptionHandler.java`
      (mesma estrutura de T002, mapeando `PaymentNotFoundException`) (depende de T003)
- [X] T005 [P] [US1] Criar `invoice-api/src/main/java/com/orderslab/invoice_api/exception/ErrorResponse.java`
      (mesma estrutura de T001, independente)
- [X] T006 [US1] Criar `invoice-api/src/main/java/com/orderslab/invoice_api/exception/GlobalExceptionHandler.java`
      (mesma estrutura de T002, mapeando `InvoiceNotFoundException`) (depende de T005)
- [X] T007 [US1] Subir `order-api` (`./mvnw spring-boot:run`) e `curl` um id inexistente em
      `GET /api/orders/{id}`; confirmar status `404` (depende de T002); ver `quickstart.md`
- [X] T008 [US1] Repetir T007 para `payment-api` (`GET /api/payments/{id}`) (depende de T004)
- [X] T009 [US1] Repetir T007 para `invoice-api` (`GET /api/invoices/{id}`) (depende de T006)

**Checkpoint**: os 3 serviços retornam 404 para id inexistente.

---

## Fase 4: User Story 2 - Corpo de erro estruturado e consistente (Priority: P2)

**Goal**: O corpo `404` contém os 5 campos definidos.

**Independent Test**: inspecionar o JSON capturado em T007–T009.

### Implementation for User Story 2

- [X] T010 [US2] A partir das respostas capturadas em T007–T009, confirmar que o corpo de
      cada uma contém `timestamp`, `status`, `error`, `message` e `path` (depende de
      T007, T008, T009)

**Checkpoint**: corpo de erro estruturado confirmado nos 3 serviços.

---

## Fase 5: User Story 3 - Mesmo formato nos 3 serviços, implementado independente (Priority: P3)

**Goal**: Os 3 corpos de erro têm exatamente os mesmos nomes de campo.

**Independent Test**: comparação lado a lado dos 3 JSONs.

### Implementation for User Story 3

- [X] T011 [US3] Comparar os 3 corpos capturados e confirmar mesmos nomes/tipos de campo,
      conforme `contracts/error-404.md` (depende de T010)

**Checkpoint**: consistência de contrato entre os 3 serviços confirmada.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T012 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar `BUILD SUCCESS` e que
      a suíte de testes existente continua passando (não-regressão de sucesso 200/201)
- [X] T013 [P] Atualizar o item 1.3 de `ROADMAP.md` como concluído
- [X] T014 Commitar os 6 arquivos novos e `specs/003-http-404-error-handling/` na branch
      `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001/T003/T005 (os 3 `ErrorResponse`) são totalmente independentes — paralelizáveis.
- T002 depende de T001; T004 depende de T003; T006 depende de T005 — mas os 3 pares
  (serviço a serviço) são independentes entre si.
- T007/T008/T009 dependem do par correspondente (T002/T004/T006) — independentes entre si.
- T010 depende de T007+T008+T009. T011 depende de T010.
- T012/T013 podem rodar em paralelo após T011. T014 depende de ambos.

## Implementation Strategy

### MVP First (User Story 1)

1. T001→T002 (order-api), T003→T004 (payment-api), T005→T006 (invoice-api) — os 3 pares em
   paralelo.
2. T007, T008, T009 — validar 404 nos 3 serviços.
3. **STOP and VALIDATE**: se os 3 retornam 404, o MVP desta feature está pronto.

### Incremental Delivery

1. US1 (T001–T009) → 404 real nos 3 serviços.
2. US2 (T010) → confirma corpo estruturado (já entregue pela mesma implementação de US1).
3. US3 (T011) → confirma consistência entre serviços.
4. Polish (T012–T014) → build, ROADMAP, commit.

## Notes

- US1 e US2 são entregues pela mesma implementação (o handler já produz o corpo estruturado
  ao mesmo tempo que corrige o status) — por isso T010/T011 são tarefas de validação, não de
  código novo.
- Nenhum controller nem `*NotFoundException` existente é alterado.
