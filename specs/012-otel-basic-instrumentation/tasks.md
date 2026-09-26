# Tasks: Instrumentação Básica com OpenTelemetry

**Input**: Design documents from `/specs/012-otel-basic-instrumentation/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: nenhum teste novo — validação é via `quickstart.md` (inspeção manual de
logs/métricas) e reconfirmação de que os 66 testes existentes (itens 1.6-1.11) continuam
passando sem alteração de comportamento.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

Nenhum pré-requisito externo bloqueante (diferente do item 1.10/1.11, esta feature não
depende de nenhuma infra já provisionada).

---

## Fase 3: User Story 1 - Toda requisição gera um trace distribuído rastreável (Priority: P1) 🎯 MVP

**Goal**: os 3 serviços geram traces distribuídos reais para toda requisição HTTP, sem exigir
nenhum Collector/backend rodando.

**Independent Test**: fazer uma requisição HTTP a qualquer serviço e confirmar, nos logs, um
identificador de trace único associado a ela (ver `quickstart.md`).

### Implementation for User Story 1 — order-api

- [X] T001 [P] [US1] `order-api`: wiring completo — adicionar
      `spring-boot-starter-opentelemetry` (sem `<version>`) em `pom.xml`; adicionar em
      `application.properties`: `management.tracing.sampling.probability=1.0`,
      `logging.structured.format.console=ecs`,
      `management.endpoints.web.exposure.include=health,metrics`
- [X] T002 [US1] `order-api`: subir o serviço e validar (`quickstart.md`) — uma requisição
      HTTP produz um trace com identificador único nos logs (SC-001); o serviço inicia e
      responde normalmente mesmo sem nenhum Collector escutando em `localhost:4318`
      (FR-006/SC-003); `/actuator/metrics` expõe `http.server.requests` (FR-005) (depende de
      T001)

### Implementation for User Story 1 — payment-api

- [X] T003 [P] [US1] `payment-api`: mesmo wiring de T001, adaptado
- [X] T004 [US1] `payment-api`: mesma validação de T002 (depende de T003)

### Implementation for User Story 1 — invoice-api

- [X] T005 [P] [US1] `invoice-api`: mesmo wiring de T001, adaptado
- [X] T006 [US1] `invoice-api`: mesma validação de T002 (depende de T005)

**Checkpoint**: os 3 serviços geram traces reais por requisição, sem depender de nenhum
Collector/backend.

---

## Fase 4: User Story 2 - Logs de transição de estado correlacionáveis ao trace (Priority: P2)

**Goal**: os logs de criar/confirmar-emitir/cancelar de cada serviço são estruturados e
carregam o mesmo identificador de trace da requisição HTTP que os originou.

**Independent Test**: disparar uma transição de estado e confirmar que o log gerado é
estruturado e contém o trace_id da requisição que a originou (ver `quickstart.md`).

### Implementation for User Story 2 — order-api

- [X] T007 [US2] `order-api`: adicionar `Logger` (SLF4J) em `service/OrderService.java` e
      logar via API fluente (`log.atInfo().addKeyValue(...).log(...)`) nos pontos `create`/
      `confirm`/`cancel` (depende de T002)
- [X] T008 [US2] `order-api`: validar (`quickstart.md`) — o trace_id do log de transição de
      estado é idêntico ao da requisição HTTP que o originou (SC-002) (depende de T007)

### Implementation for User Story 2 — payment-api

- [X] T009 [US2] `payment-api`: mesmo padrão de T007 em `service/PaymentService.java`
      (`create`/`confirm`/`cancel`) (depende de T004)
- [X] T010 [US2] `payment-api`: mesma validação de T008 (depende de T009)

### Implementation for User Story 2 — invoice-api

- [X] T011 [US2] `invoice-api`: mesmo padrão de T007 em `service/InvoiceService.java`
      (`create`/`issue`/`cancel`) (depende de T006)
- [X] T012 [US2] `invoice-api`: mesma validação de T008 (depende de T011)

**Checkpoint**: logs de transição de estado nos 3 serviços são estruturados e correlacionáveis
ao trace da requisição que os originou.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T013 [P] Rodar `./mvnw test` nos 3 serviços — confirmar que os 66 testes existentes
      (itens 1.6-1.11) continuam passando sem alteração de comportamento (SC-004/FR-007)
- [X] T014 [P] Atualizar o item 1.12 de `ROADMAP.md` como concluído e marcar a Fase 1 como
      completa
- [X] T015 Commitar as edições dos 3 serviços e `specs/012-otel-basic-instrumentation/` na
      branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- Dentro de cada serviço: wiring (US1) → validação (US1) → Logger no Service (US2) →
  validação (US2) — sequencial.
- Os 3 blocos de serviço são independentes entre si — o wiring inicial (T001/T003/T005) é
  paralelizável; o restante de cada bloco é sequencial internamente.
- Polish depende dos 3 blocos completos (US1+US2).

## Implementation Strategy

### MVP First (User Story 1)

1. Os 3 blocos de wiring (T001/T003/T005) — podem ser feitos em sequência ou intercalados.
2. Validar T002/T004/T006 — traces reais, serviço resiliente sem Collector.
3. **STOP and VALIDATE**: se os 3 serviços geram traces sem depender de infra externa, o MVP
   está pronto.

### Incremental Delivery

1. US1 (T001-T006) → traces distribuídos reais nos 3 serviços.
2. US2 (T007-T012) → logs de transição de estado correlacionáveis ao trace.
3. Polish (T013-T015) → suíte de testes reconfirmada, ROADMAP (Fase 1 completa), commit.

## Notes

- Nenhuma mudança em `Controller`/`Repository`/`Model`/migrations/testes — contrato HTTP e
  comportamento de produção idênticos.
- Nenhum OTel Collector/SigNoz implantado nesta feature — isso é escopo da Fase 6, já
  sequenciada no ROADMAP.
- Ao concluir este item, a Fase 1 do ROADMAP fica completa; o próximo passo é a Fase 2
  (Kafka assíncrono).
