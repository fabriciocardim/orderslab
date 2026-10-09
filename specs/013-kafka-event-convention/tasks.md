# Tasks: Convenção de Evento/Tópico Kafka

**Input**: Design documents from `/specs/013-kafka-event-convention/`

**Prerequisites**: plan.md, spec.md, research.md, contracts/event-contract.md, quickstart.md

**Tests**: não aplicável — feature puramente documental, sem código novo. A "validação" é a
conferência do próprio contrato (`quickstart.md`).

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

---

## Fase 3: User Story 1 - Todo evento pode ser rastreado até o pedido de origem (Priority: P1) 🎯 MVP

**Goal**: confirmar que o contrato documentado exige `orderId` sem ambiguidade em todos os 7
eventos, incluindo os que não são diretamente sobre um pedido.

**Independent Test**: inspecionar `contracts/event-contract.md` e confirmar `orderId`
obrigatório em todos os 7 eventos.

- [X] T001 [US1] Confirmar (`quickstart.md` Verificação 3) que `orderId` está presente e
      obrigatório no envelope dos 7 eventos do contrato — incluindo `PaymentReserved`/
      `PaymentFailed`/`InvoiceIssued`/`InvoiceFailed`, que não são diretamente sobre um
      pedido
- [X] T002 [US1] Confirmar (`quickstart.md` Verificação 2) que a tabela de tópicos em
      `contracts/event-contract.md` resolve, sem ambiguidade, um nome de tópico único para
      cada um dos 7 eventos já nomeados no ROADMAP (depende de T001)

**Checkpoint**: contrato de correlação (`orderId`) e de nomeação de tópico validados.

---

## Fase 4: User Story 2 - Cada serviço implementa de forma independente, sem lib compartilhada (Priority: P2)

**Goal**: confirmar que a convenção documentada não introduz nenhuma dependência de código
nova nem compartilhada entre os 3 serviços.

**Independent Test**: confirmar que nenhum `pom.xml`/código de produção foi alterado, e que
o mecanismo de serialização escolhido já está disponível nos 3 serviços sem dependência nova.

- [X] T003 [US2] Confirmar (`quickstart.md` Verificação 1) que `spring-kafka:4.1.1` já está
      no classpath dos 3 serviços via `spring-boot-starter-kafka` (item 1.1) — nenhuma
      dependência Maven nova é exigida por esta decisão
- [X] T004 [US2] Confirmar (`quickstart.md` Verificação 4) que nenhum `pom.xml`/arquivo de
      código de produção dos 3 serviços foi alterado por esta feature — só documentação em
      `specs/013-kafka-event-convention/` (depende de T003)

**Checkpoint**: zero dependência de código compartilhada confirmada; decisão pronta para ser
consumida por E2.2 (`order-api` produtor), o próximo item.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T005 Atualizar o item E2.1 de `ROADMAP.md` como concluído
- [X] T006 Commitar `specs/013-kafka-event-convention/` (e o `ROADMAP.md` atualizado) na
      branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- US1 (T001-T002) e US2 (T003-T004) são independentes entre si — podem ser feitos em
  qualquer ordem.
- Polish depende de US1+US2 completos.

## Implementation Strategy

### MVP First (User Story 1)

1. T001-T002 — confirmar a regra de correlação e a tabela de tópicos.
2. **STOP and VALIDATE**: se `orderId` é obrigatório em todo evento e cada tópico é
   inequívoco, o MVP desta decisão está pronto para ser consumido por E2.2.

### Incremental Delivery

1. US1 (T001-T002) → contrato de correlação/tópico validado.
2. US2 (T003-T004) → independência de código confirmada.
3. Polish (T005-T006) → ROADMAP, commit.

## Notes

- Esta feature não altera nenhum arquivo de produção — só documentação.
- O próximo item do ROADMAP (E2.2, produtor real em `order-api`) consome diretamente
  `contracts/event-contract.md`.
