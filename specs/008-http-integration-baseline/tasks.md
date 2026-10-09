# Tasks: Teste de Integração HTTP Baseline

**Input**: Design documents from `/specs/008-http-integration-baseline/`

**Prerequisites**: plan.md, spec.md, research.md

**Tests**: esta feature É o teste de integração — não há um segundo nível de teste sobre ela.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

---

## Fase 3: User Story 1 - Fluxo real ponta a ponta é validado sem nenhum mock (Priority: P1) 🎯 MVP

**Goal**: cada serviço tem um teste de integração real (servidor real, sem mocks) cobrindo
criar → transicionar → buscar.

**Independent Test**: `./mvnw test` em cada serviço, teste de integração passando.

### Implementation for User Story 1

- [X] T001 [P] [US1] Reescrever `order-api/src/test/java/com/orderslab/order_api/OrderApiApplicationTests.java`
      (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`):
      criar pedido → `confirm` → `GET` confirma `CONFIRMED`; segundo pedido → `cancel` →
      confirma `200`
- [X] T002 [P] [US1] Reescrever `payment-api/.../PaymentApiApplicationTests.java` com o
      mesmo fluxo, adaptado a `payment-api` (`reserve` → `confirm`/`cancel`)
- [X] T003 [P] [US1] Reescrever `invoice-api/.../InvoiceApiApplicationTests.java` com o
      mesmo fluxo, adaptado a `invoice-api` (`create` → `issue`/`cancel`)
- [X] T004 [US1] Rodar `./mvnw test` nos 3 serviços; confirmar todos os testes (itens 1.6,
      1.7 e este) passando (depende de T001, T002, T003)

**Checkpoint**: os 3 serviços têm um teste de integração real ponta a ponta passando.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T005 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar `BUILD SUCCESS`
      completo
- [X] T006 [P] Atualizar o item 1.8 de `ROADMAP.md` como concluído
- [X] T007 Commitar os 3 arquivos reescritos e `specs/008-http-integration-baseline/` na
      branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001/T002/T003 independentes entre si — paralelizáveis.
- T004 depende dos 3. Polish depende de T004.

## Implementation Strategy

### MVP First (única user story)

1. T001–T003 em paralelo.
2. T004 — confirmar tudo passando (itens 1.6+1.7+1.8 juntos).
3. **STOP and VALIDATE**: se os 3 `./mvnw test` passam, a feature está pronta.

## Notes

- Nenhum arquivo em `src/main/` é alterado por esta feature.
- Esta é a última feature de teste da Fase 1 antes de tocar em persistência (itens 1.9/1.10)
  — depois disso, a suíte inteira (itens 1.6-1.8) precisa continuar passando como rede de
  segurança contra regressão.
