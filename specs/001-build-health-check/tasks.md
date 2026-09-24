# Tasks: Build Health Check e Correção de Dependências

**Input**: Design documents from `/specs/001-build-health-check/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Não solicitados nem aplicáveis — feature não introduz código novo.

**Organização**: A pesquisa (Fase 0 do plano) já resolveu ambas as user stories — os 3
serviços já buildam com sucesso e os artifact IDs já são válidos. O trabalho restante é
revalidar e formalizar isso em documentação, não escrever código.

## Fase 1: Setup — não aplicável

Nenhuma inicialização de projeto necessária; os 3 serviços já existem e já buildam.

## Fase 2: Foundational — não aplicável

Nenhum pré-requisito bloqueante novo; a investigação já foi concluída em `research.md`.

---

## Fase 3: User Story 1 - Build confiável nos 3 serviços (Priority: P1) 🎯 MVP

**Goal**: Confirmar que `./mvnw clean verify` passa nos 3 serviços antes de fechar a feature.

**Independent Test**: Rodar `./mvnw clean verify` em cada serviço e observar `BUILD SUCCESS`.

### Implementation for User Story 1

- [X] T001 [US1] Revalidar `./mvnw clean verify` (BUILD SUCCESS, exit code 0) em
      `order-api/`, `payment-api/` e `invoice-api/`, confirmando que nada mudou desde a
      investigação registrada em `specs/001-build-health-check/research.md`

**Checkpoint**: Build dos 3 serviços confirmado saudável.

---

## Fase 4: User Story 2 - Estrutura de dependências de teste confirmada como intencional (Priority: P2)

**Goal**: Confirmar que os 3 starters `-test` por serviço não são redundância, e sim o
padrão granular do Spring Boot 4.1.1.

**Independent Test**: Inspecionar `./mvnw dependency:tree` de um serviço e verificar que
cada `-test` traz ferramentas distintas.

### Implementation for User Story 2

- [X] T002 [P] [US2] Revisar `./mvnw dependency:tree` de `order-api/` (evidência já
      coletada em `specs/001-build-health-check/research.md`) e confirmar, como dupla
      checagem final, que `spring-boot-starter-webmvc-test`,
      `spring-boot-starter-kafka-test` e `spring-boot-starter-actuator-test` não se
      sobrepõem — cada um resolve dependências transitivas distintas

**Checkpoint**: Nenhuma alteração de dependências necessária nos 3 `pom.xml`.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T003 [P] Atualizar o item 1.1 de `ROADMAP.md` para refletir que o build já está
      saudável e os artifact IDs já são válidos no Spring Boot 4.1.1 (starters renomeados/
      reestruturados em relação ao Boot 3.x), para não reabrir a suspeita numa fase futura —
      arquivo: `ROADMAP.md`
- [X] T004 Rodar `quickstart.md` (`specs/001-build-health-check/quickstart.md`) do início ao
      fim como validação final end-to-end da feature
- [X] T005 Commitar `spec.md`, `research.md`, `plan.md`, `quickstart.md`, `tasks.md` e o
      `ROADMAP.md` atualizado na branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup / Foundational**: não aplicável, sem bloqueio.
- **User Story 1 (T001)** e **User Story 2 (T002)**: independentes entre si, podem rodar em
  paralelo — nenhuma depende do resultado da outra.
- **Polish (T003–T005)**: depende da confirmação de T001 e T002 (ambas já apontam para
  "nenhuma mudança de código necessária", então T003 só formaliza isso no ROADMAP).

### Parallel Opportunities

- T001 e T002 podem ser executados em paralelo (T002 já está marcado `[P]`).
- T003 pode ser feito em paralelo a T001/T002 já que o conteúdo da atualização já é
  conhecido via `research.md`, mas semanticamente faz mais sentido depois da revalidação.

---

## Implementation Strategy

### MVP First (User Story 1)

1. T001 — revalidar o build dos 3 serviços.
2. **STOP and VALIDATE**: se `BUILD SUCCESS` nos 3, a suspeita original está oficialmente
   encerrada.

### Incremental Delivery

1. T001 (US1) → confirma build saudável.
2. T002 (US2) → confirma que dependências de teste são intencionais.
3. T003–T005 → formaliza os achados em `ROADMAP.md` e commita tudo.

## Notes

- Esta feature não segue o padrão típico "Setup → Foundational → código por user story"
  porque a pesquisa (Fase 0 do plano) já resolveu o problema antes de qualquer tarefa de
  implementação ser necessária — o valor entregue é a confirmação formal, não código novo.
- Nenhum arquivo de aplicação (`order-api/`, `payment-api/`, `invoice-api/` código-fonte)
  é alterado por esta feature.
