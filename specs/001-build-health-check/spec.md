# Feature Specification: Build Health Check e Correção de Dependências

**Feature Branch**: `001-build-health-check`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Build health check dos 3 microsserviços (order-api, payment-api, invoice-api). Rodar `./mvnw clean verify` em cada um e confirmar se o build passa de verdade. Investigar em especial se os artifact IDs declarados nos pom.xml — `spring-boot-starter-webmvc`, `spring-boot-starter-kafka` e as variantes `-test` (`spring-boot-starter-webmvc-test`, `spring-boot-starter-kafka-test`, `spring-boot-starter-actuator-test`) — são coordinates reais que resolvem no BOM do Spring Boot 4.1.1 usado pelo projeto, ou se são nomes incorretos. Se não forem canônicos/não resolverem, corrigir para os artifact IDs padrão do Spring Boot (`spring-boot-starter-web`, `spring-kafka`) e consolidar as dependências de teste em um único `spring-boot-starter-test` por serviço, evitando duplicação. O resultado esperado é: os 3 serviços buildam com `mvnw clean verify` sem erro, usando dependências corretas e sem redundância. Este é o item 1.1 do ROADMAP.md da Fase 1, pré-requisito para todos os demais itens da fase."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build confiável nos 3 serviços (Priority: P1)

Como desenvolvedor do laboratório, quero rodar `./mvnw clean verify` em cada um dos 3
serviços e ter certeza de que o build passa de verdade, para poder confiar no pipeline de
CI e não descobrir problemas de dependência só em produção ou numa fase futura.

**Why this priority**: é pré-requisito bloqueante para todo o restante da Fase 1 (itens
1.2–1.12 do ROADMAP) — sem build confiável, qualquer coisa construída em cima é instável.

**Independent Test**: rodar `./mvnw clean verify` em `order-api`, `payment-api` e
`invoice-api`, cada um isoladamente, e observar `BUILD SUCCESS` / exit code 0.

**Acceptance Scenarios**:

1. **Given** o `pom.xml` de um serviço com um artifact ID que não existe no BOM do Spring
   Boot usado, **When** `./mvnw clean verify` é executado, **Then** o build falha com erro
   de resolução de dependência (confirmando o problema) — ou o artifact é confirmado válido.
2. **Given** os 3 `pom.xml` com coordinates canônicos confirmados, **When**
   `./mvnw clean verify` é executado em cada serviço, **Then** o build finaliza com
   `BUILD SUCCESS`, sem erros de dependência.

---

### User Story 2 - Confirmar que a estrutura de dependências de teste é intencional (Priority: P2)

> **Resolvido via pesquisa (ver `research.md`)**: a investigação (Fase 0 do plano) mostrou
> que os 3 starters `-test` (`-webmvc-test`, `-kafka-test`, `-actuator-test`) **não são
> redundantes** — são o padrão granular oficial do Spring Boot 4.1.1, cada um trazendo as
> ferramentas de teste específicas do seu starter correspondente. Esta user story original
> pedia "consolidar num único `spring-boot-starter-test`", o que se mostrou uma premissa
> incorreta (baseada em convenção do Spring Boot 3.x). Nenhuma consolidação é necessária.

Como desenvolvedor, quero confirmar que a estrutura de dependências de teste dos 3 serviços
é a intencional (granular por starter, padrão Boot 4), e não uma duplicação acidental, para
não gastar esforço "corrigindo" algo que já está correto.

**Why this priority**: menos crítico que garantir que o build simplesmente funciona (P1),
mas evita que a suspeita original seja reaberta por engano numa fase futura.

**Independent Test**: inspecionar a árvore de dependências resolvida
(`./mvnw dependency:tree`) de cada serviço e confirmar que cada `-test` traz ferramentas
distintas e específicas do seu starter, sem duplicação real de conteúdo.

**Acceptance Scenarios**:

1. **Given** os 3 `pom.xml` com os starters `-webmvc-test`/`-kafka-test`/`-actuator-test`,
   **When** a árvore de dependências é inspecionada, **Then** cada um traz ferramentas de
   teste específicas e não sobrepostas ao seu starter correspondente — confirmando que a
   estrutura é intencional, não redundante.

---

### Edge Cases

- O que acontece se um artifact ID atual (ex.: `spring-boot-starter-webmvc`) na verdade
  resolver corretamente, por ser um nome válido no Spring Boot 4.1.1? Não deve ser alterado
  só por parecer suspeito — a correção só se aplica quando o build falhar de fato ou for
  confirmado que o coordinate não é o padrão documentado pelo Spring Boot.
- O que acontece se os 3 serviços tiverem divergências entre si (ex.: um usa o nome certo,
  outro não)? Cada serviço MUST ser corrigido de forma independente, preservando a
  Independência dos Serviços (Princípio I da constitution).
- O que acontece se a correção de uma dependência quebrar código existente que dependia do
  nome antigo? Não é esperado — um artifact ID incorreto não compila nada hoje; se compilava,
  é porque já era válido e não precisa de correção.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O build de cada um dos 3 serviços (`order-api`, `payment-api`, `invoice-api`)
  MUST ser executável via `./mvnw clean verify` a partir do diretório do próprio serviço,
  sem erros de resolução de dependência.
- **FR-002**: Cada dependência declarada nos 3 `pom.xml` MUST corresponder a um artifact ID
  real e resolvível no BOM do Spring Boot usado pelo projeto (4.1.1) ou no Maven Central, não
  a um nome inventado ou incorreto.
- **FR-003**: ~~As dependências de teste de cada serviço MUST estar consolidadas em uma
  única dependência de escopo `test`~~ — **resolvido via pesquisa**: os 3 starters `-test`
  granulares já declarados (um por starter de funcionalidade) MUST ser mantidos como estão,
  pois refletem o padrão oficial do Spring Boot 4.1.1, sem redundância real entre eles.
- **FR-004**: A correção aplicada a um serviço MUST ser replicada de forma independente nos
  outros dois, sem introduzir dependência de código entre os módulos (Princípio I).
- **FR-005**: O resultado final MUST ser verificável de forma objetiva: `./mvnw clean verify`
  retorna `BUILD SUCCESS` (exit code 0) nos 3 serviços.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Os 3 serviços completam `./mvnw clean verify` com sucesso (exit code 0), sem
  nenhuma falha de resolução de dependência.
- **SC-002**: 100% das dependências declaradas nos 3 `pom.xml` resolvem no BOM do Spring
  Boot/Maven Central — nenhum artifact ID não-canônico ou inexistente resta.
- **SC-003**: ~~O número de dependências de escopo `test` por serviço é reduzido para uma
  única entrada consolidada~~ — **resolvido via pesquisa**: confirmado que as 3 variantes
  `-test` por serviço são intencionais (padrão Boot 4), não uma duplicação a ser reduzida.
- **SC-004**: Um desenvolvedor novo consegue clonar o repositório e rodar
  `./mvnw clean verify` em qualquer um dos 3 serviços, pela primeira vez, sem precisar
  investigar ou corrigir dependências manualmente.

## Assumptions

- O ambiente de execução tem acesso ao Maven Central para baixar dependências (sem
  proxy/mirror corporativo bloqueando).
- A versão do Spring Boot (4.1.1) e do Java (21) já fixadas no projeto não mudam como parte
  desta feature — o escopo é corrigir artifact IDs incorretos, não fazer upgrade/downgrade.
- Corrigir artifact IDs não deve afetar comportamento em runtime (são nomes de dependência,
  não uso de API diferente); se algum artifact realmente não existir hoje, sua substituição
  pelo equivalente correto está dentro do escopo desta feature.
- Este item cobre só verificação/correção de build — não inclui escrever novos testes
  automatizados (isso é o item 1.6/1.7/1.8 do ROADMAP, fora de escopo aqui).
