# Tasks: Remover Toolchain Kotlin/Lombok Morto

**Input**: Design documents from `/specs/002-remove-kotlin-lombok/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Não solicitados — feature não introduz código Java novo, só remove configuração
de build morta.

## Fase 1: Setup — não aplicável

Nenhuma inicialização necessária; os 2 serviços já existem e já buildam.

## Fase 2: Foundational — não aplicável

Nenhum pré-requisito bloqueante novo; o mapeamento exato dos blocos a remover já está em
`research.md`.

---

## Fase 3: User Story 1 - pom.xml enxutos, sem toolchain não utilizado (Priority: P1) 🎯 MVP

**Goal**: Remover dependências/plugins Kotlin e Lombok não utilizados dos 2 `pom.xml`,
mantendo o build verde.

**Independent Test**: `grep -i "kotlin\|lombok"` retorna vazio nos 2 `pom.xml`, e
`./mvnw clean verify` continua `BUILD SUCCESS` nos 2 serviços.

### Implementation for User Story 1

- [X] T001 [P] [US1] Remover de `payment-api/pom.xml`: propriedade `kotlin.version`;
      dependências `org.jetbrains.kotlin:kotlin-stdlib`, `org.jetbrains.kotlin:kotlin-test`,
      `org.projectlombok:lombok`; o plugin `kotlin-maven-plugin` inteiro (com seus 3
      sub-plugins `kotlin-maven-noarg`/`kotlin-maven-lombok`/`kotlin-maven-allopen`); e os
      2 blocos `<annotationProcessorPaths>` de Lombok dentro do `maven-compiler-plugin`
      (`default-compile` e `default-testCompile`) — mapeamento exato em `research.md`
- [X] T002 [P] [US1] Aplicar a mesma remoção, de forma independente, em
      `invoice-api/pom.xml` (estrutura idêntica a `payment-api/pom.xml`, exceto
      `artifactId`)
- [X] T003 [US1] Rodar `./mvnw clean verify` em `payment-api/` e confirmar `BUILD SUCCESS`
      (depende de T001)
- [X] T004 [US1] Rodar `./mvnw clean verify` em `invoice-api/` e confirmar `BUILD SUCCESS`
      (depende de T002)

**Checkpoint**: Ambos os `pom.xml` sem Kotlin/Lombok, ambos os builds verdes.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T005 [P] Confirmar `git diff --stat` mostrando só `payment-api/pom.xml` e
      `invoice-api/pom.xml` alterados — nenhum arquivo `.java` tocado
- [X] T006 Commitar `payment-api/pom.xml`, `invoice-api/pom.xml` e
      `specs/002-remove-kotlin-lombok/` na branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001 e T002 são independentes entre si (arquivos diferentes) — podem rodar em paralelo.
- T003 depende de T001; T004 depende de T002. T003/T004 também podem rodar em paralelo
  entre si.
- T005 depende de T003 e T004 (build confirmado antes de fechar).
- T006 depende de T005.

## Implementation Strategy

### MVP First (User Story 1)

1. T001 + T002 em paralelo → remove o toolchain morto dos 2 `pom.xml`.
2. T003 + T004 em paralelo → confirma que os 2 builds continuam verdes.
3. **STOP and VALIDATE**: se ambos `BUILD SUCCESS`, a feature está pronta para commit.

### Incremental Delivery

1. T001–T004 → remoção + validação nos 2 serviços.
2. T005–T006 → confirmação final de escopo e commit.

## Notes

- Única user story (P1) — feature pequena e mecânica, sem necessidade de MVP incremental
  entre múltiplas stories.
- Nenhum arquivo `.java` é alterado por esta feature.
