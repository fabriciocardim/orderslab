# Tasks: Validação de Request (Bean Validation)

**Input**: Design documents from `/specs/004-request-validation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Validação empírica via boot + `curl` (mesmo padrão do item 1.3); testes
automatizados formais ficam para os itens 1.6/1.7 do ROADMAP.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational (bloqueia as user stories)

**Purpose**: dependência de validação e extensão do `ErrorResponse` precisam existir antes
de qualquer anotação/handler ser adicionado.

- [X] T001 [P] Adicionar `spring-boot-starter-validation` a `order-api/pom.xml`
- [X] T002 [P] Adicionar `spring-boot-starter-validation` a `payment-api/pom.xml`
- [X] T003 [P] Adicionar `spring-boot-starter-validation` a `invoice-api/pom.xml`
- [X] T004 [P] Estender `order-api/src/main/java/com/orderslab/order_api/exception/ErrorResponse.java`
      com o componente `List<String> validationErrors` + construtor secundário de 5
      argumentos (ver `research.md`, Decisão 4)
- [X] T005 [P] Estender `payment-api/.../exception/ErrorResponse.java` da mesma forma
- [X] T006 [P] Estender `invoice-api/.../exception/ErrorResponse.java` da mesma forma

**Checkpoint**: dependência de validação resolvível e `ErrorResponse` pronto para o novo
campo nos 3 serviços.

---

## Fase 3: User Story 1 - Requisição inválida é rejeitada com 400 (Priority: P1) 🎯 MVP

**Goal**: campo obrigatório ausente/`amount` ≤ 0 retorna 400 nos 3 serviços.

**Independent Test**: `curl` com `customerId` vazio ou `amount` negativo em cada serviço,
observar `400`.

### Implementation for User Story 1

- [X] T007 [P] [US1] Anotar `order-api/.../dto/OrderRequest.java`
      (`customerId`: `@NotBlank`; `amount`: `@NotNull @Positive`) (depende de T001)
- [X] T008 [US1] Adicionar `@Valid` ao parâmetro `@RequestBody` de
      `OrderController.create()` (depende de T007)
- [X] T009 [US1] Adicionar `@ExceptionHandler(MethodArgumentNotValidException.class)` em
      `order-api/.../exception/GlobalExceptionHandler.java`, retornando 400 com
      `ErrorResponse` (`validationErrors` populado a partir de `BindingResult`) (depende de
      T004)
- [X] T010 [P] [US1] Anotar `payment-api/.../dto/PaymentReservationRequest.java`
      (`orderId`: `@NotBlank`; `amount`: `@NotNull @Positive`) (depende de T002)
- [X] T011 [US1] Adicionar `@Valid` a `PaymentController.reserve()` (depende de T010)
- [X] T012 [US1] Adicionar o mesmo handler de T009 em
      `payment-api/.../exception/GlobalExceptionHandler.java` (depende de T005)
- [X] T013 [P] [US1] Anotar `invoice-api/.../dto/InvoiceRequest.java`
      (`orderId`/`paymentId`: `@NotBlank`; `amount`: `@NotNull @Positive`) (depende de T003)
- [X] T014 [US1] Adicionar `@Valid` a `InvoiceController.create()` (depende de T013)
- [X] T015 [US1] Adicionar o mesmo handler de T009 em
      `invoice-api/.../exception/GlobalExceptionHandler.java` (depende de T006)
- [X] T016 [US1] Subir os 3 serviços e `curl` `POST` com campo obrigatório
      ausente/`amount` negativo em cada um; confirmar `400` (depende de T008, T009, T011,
      T012, T014, T015)
- [X] T017 [US1] Na mesma sessão de T016, `curl` `POST` com dados válidos nos 3 serviços;
      confirmar `201` sem regressão

**Checkpoint**: os 3 serviços rejeitam requisição inválida com 400; sucesso continua 201.

---

## Fase 4: User Story 2 - Formato de referência entre serviços é validado (Priority: P2)

**Goal**: `orderId`/`paymentId` fora do formato UUID retornam 400.

**Independent Test**: `curl` com `orderId="abc"` em `payment-api`/`invoice-api`, observar
`400`.

### Implementation for User Story 2

- [X] T018 [P] [US2] Adicionar `@Pattern(regexp = UUID_REGEX)` a
      `PaymentReservationRequest.orderId` (depende de T010)
- [X] T019 [P] [US2] Adicionar `@Pattern(regexp = UUID_REGEX)` a `InvoiceRequest.orderId` e
      `InvoiceRequest.paymentId` (depende de T013)
- [X] T020 [US2] `curl` `POST /api/payments` e `POST /api/invoices` com `orderId`/`paymentId`
      que não é UUID (ex.: `"abc"`); confirmar `400` (depende de T018, T019)
- [X] T021 [US2] `curl` com um UUID sintaticamente válido (mesmo que não exista de fato);
      confirmar que passa desta validação de formato — sem regressão do caminho de sucesso

**Checkpoint**: `orderId`/`paymentId` com formato inválido são rejeitados nos 2 serviços.

---

## Fase 5: User Story 3 - Resposta identifica todos os campos inválidos (Priority: P3)

**Goal**: 2+ campos inválidos simultâneos aparecem todos na resposta.

**Independent Test**: `curl` com `customerId` vazio **e** `amount` negativo ao mesmo tempo.

### Implementation for User Story 3

- [X] T022 [US3] `curl POST /api/orders` com `customerId` vazio e `amount` negativo ao mesmo
      tempo; confirmar que `validationErrors` lista os 2 problemas (depende de T016)

**Checkpoint**: corpo de erro lista múltiplos problemas quando aplicável.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T023 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar `BUILD SUCCESS` e
      não-regressão da suíte existente
- [X] T024 [P] Atualizar o item 1.4 de `ROADMAP.md` como concluído
- [X] T025 Commitar as edições nos 3 serviços e `specs/004-request-validation/` na branch
      `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001–T006 (Foundational) são todas paralelizáveis entre si (arquivos diferentes).
- Dentro de cada serviço, US1 segue: anotar DTO → `@Valid` no controller → handler no
  `GlobalExceptionHandler` — os 3 serviços em paralelo entre si.
- T016/T017 dependem de todos os 6 pares US1 completos.
- US2 (T018/T019) depende dos DTOs já anotados em US1 (T010/T013) — adiciona `@Pattern` aos
  mesmos campos.
- US3 (T022) depende de T016 (ambiente de verificação já rodando/validado).
- Polish depende de todas as user stories completas.

## Implementation Strategy

### MVP First (User Story 1)

1. T001–T006 (Foundational) em paralelo.
2. T007–T015 (3 pares serviço a serviço) em paralelo entre si.
3. T016/T017 — validar 400/201 nos 3 serviços.
4. **STOP and VALIDATE**: se os 3 rejeitam inválido e aceitam válido, o MVP está pronto.

### Incremental Delivery

1. Foundational → US1 (T007–T017) → 400 real para campos obrigatórios/`amount` inválido.
2. US2 (T018–T021) → formato UUID validado em `payment-api`/`invoice-api`.
3. US3 (T022) → confirma resposta multi-campo.
4. Polish (T023–T025) → build, ROADMAP, commit.

## Notes

- Nenhum arquivo `Model`/`*Response.java` é alterado (decisão registrada na spec e em
  `research.md`).
- `order-api` não participa da Fase 4 (US2) — não tem campo de referência tipo `orderId`.
