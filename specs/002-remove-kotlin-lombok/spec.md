# Feature Specification: Remover Toolchain Kotlin/Lombok Morto

**Feature Branch**: `002-remove-kotlin-lombok`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Remover o toolchain Kotlin/Lombok morto dos pom.xml de payment-api e invoice-api. Hoje esses dois serviços declaram kotlin-stdlib, kotlin-maven-plugin (com plugins jpa/lombok/spring) e kotlin-test, além da dependência lombok — mas não existe nenhum arquivo .kt em todo o repositório e nenhuma anotação Lombok é usada em nenhum .java. Isso é configuração de build morta que infla o tempo de build e a superfície de manutenção/risco, além de criar uma assimetria em relação ao order-api (que não tem nada disso, é pom.xml Java puro). O objetivo é remover essas dependências/plugins não utilizados dos 2 pom.xml, mantendo o build funcionando (./mvnw clean verify continua passando com BUILD SUCCESS nos 2 serviços). Este é o item 1.2 do ROADMAP.md da Fase 1, e depende do item 1.1 (já concluído — build já confirmado saudável nos 3 serviços)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - pom.xml enxutos, sem toolchain não utilizado (Priority: P1)

Como desenvolvedor do laboratório, quero que os `pom.xml` de `payment-api` e `invoice-api`
não declarem dependências/plugins que nenhum código-fonte real usa (Kotlin, Lombok), para
reduzir superfície de manutenção, tempo de build e a assimetria em relação ao `order-api`
(que é Java puro).

**Why this priority**: é dívida técnica barata de resolver agora, antes de a Fase 1 seguir
adiante (itens 1.3+); não bloqueia tecnicamente, mas cada item novo adicionado em cima do
resíduo aumenta o custo de removê-lo depois.

**Independent Test**: inspecionar os 2 `pom.xml` e confirmar ausência de qualquer referência
a Kotlin/Lombok; rodar `./mvnw clean verify` em cada serviço e confirmar `BUILD SUCCESS`.

**Acceptance Scenarios**:

1. **Given** os `pom.xml` de `payment-api`/`invoice-api` hoje declarando `kotlin-stdlib`,
   `kotlin-test`, `kotlin-maven-plugin` (com sub-plugins `noarg`/`lombok`/`allopen`) e
   `lombok`, **When** as dependências/plugins não utilizados são removidos, **Then** o
   `pom.xml` não contém mais nenhuma referência a Kotlin ou Lombok.
2. **Given** os `pom.xml` corrigidos, **When** `./mvnw clean verify` é executado em
   `payment-api` e `invoice-api`, **Then** o build finaliza com `BUILD SUCCESS`, sem erro de
   compilação.
3. **Given** o código-fonte Java existente (controllers, services, models etc.), **When** os
   `pom.xml` são simplificados, **Then** nenhum arquivo `.java` precisa ser alterado, já que
   nenhum usa Kotlin ou Lombok hoje.

---

### Edge Cases

- O que acontece com o bloco do `maven-compiler-plugin` que referencia Lombok via
  `annotationProcessorPaths`? Deve ser removido junto, já que só existe para suportar
  anotações Lombok que não são usadas em nenhum lugar do código.
- O que acontece se o time decidir usar Kotlin em algum serviço no futuro? Isso deve ser uma
  decisão explícita e documentada (uma feature separada, reintroduzindo o toolchain de
  propósito) — não é escopo desta feature restaurar ou preservar a opção "por via das
  dúvidas".
- Remover o `kotlin-maven-plugin` quebra a fase `compile` do Maven? Não deveria — o
  `maven-compiler-plugin` padrão do Spring Boot já cobre a compilação Java; o
  `kotlin-maven-plugin` era aditivo (compilava um segundo conjunto de fontes, hoje
  inexistente), não substituto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Os `pom.xml` de `payment-api` e `invoice-api` MUST não conter nenhuma
  dependência do grupo `org.jetbrains.kotlin` (`kotlin-stdlib`, `kotlin-test`) nem o plugin
  `kotlin-maven-plugin` e seus compiler plugins associados (`kotlin-maven-noarg`,
  `kotlin-maven-lombok`, `kotlin-maven-allopen`).
- **FR-002**: Os `pom.xml` de `payment-api` e `invoice-api` MUST não conter a dependência
  `org.projectlombok:lombok` nem os `annotationProcessorPaths` do `maven-compiler-plugin`
  que a referenciam, já que nenhum código usa anotações Lombok.
- **FR-003**: A propriedade `kotlin.version` MUST ser removida de ambos os `pom.xml`, já que
  nenhuma dependência ou plugin remanescente a referencia.
- **FR-004**: Após a remoção, `./mvnw clean verify` MUST continuar retornando `BUILD SUCCESS`
  (exit code 0) em `payment-api` e `invoice-api`, sem necessidade de alterar nenhum arquivo
  `.java`.
- **FR-005**: A remoção MUST ser aplicada de forma independente em cada um dos 2 `pom.xml`
  (sem introduzir um parent/BOM compartilhado entre os serviços), preservando a
  Independência dos Serviços (Princípio I da constitution).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero ocorrências da string "kotlin" (case-insensitive) nos `pom.xml` de
  `payment-api` e `invoice-api`.
- **SC-002**: Zero ocorrências da string "lombok" nos `pom.xml` de `payment-api` e
  `invoice-api`.
- **SC-003**: `./mvnw clean verify` retorna `BUILD SUCCESS` (exit code 0) nos 2 serviços
  após a remoção.
- **SC-004**: O número de dependências/plugins Kotlin-relacionados declarados em cada
  `pom.xml` cai de 6 (2 dependências + 1 plugin com 3 sub-plugins) para 0, eliminando a
  assimetria em relação ao `order-api`.

## Assumptions

- Nenhum código atual (`.java`) depende de recursos habilitados pelo `kotlin-maven-plugin`
  (ex.: o compiler plugin `jpa`, que abre classes para JPA) — não existem entidades JPA no
  projeto ainda; isso é tratado depois, no item 1.10 do ROADMAP.
- O `maven-compiler-plugin` padrão (sem os `annotationProcessorPaths` de Lombok) continua
  suficiente para compilar o código Java existente.
- Esta feature não decide se Kotlin será usado no futuro — apenas remove o resíduo não
  utilizado hoje; adotar Kotlin formalmente seria uma decisão explícita e uma feature à
  parte.
