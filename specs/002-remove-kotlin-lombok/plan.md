# Implementation Plan: Remover Toolchain Kotlin/Lombok Morto

**Branch**: `002-remove-kotlin-lombok` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-remove-kotlin-lombok/spec.md`

## Summary

Remover do `pom.xml` de `payment-api` e `invoice-api` toda a configuração de build para
Kotlin e Lombok (dependências, plugin, sub-plugins, `annotationProcessorPaths`), confirmado
via pesquisa que nada no código-fonte usa nenhum dos dois. Ver [research.md](./research.md)
para o mapeamento exato dos blocos a remover.

## Technical Context

**Language/Version**: Java 21 (não muda; a mudança é remover suporte não usado a Kotlin)

**Primary Dependencies**: Spring Boot 4.1.1 (parent POM), Maven; remoção de
`org.jetbrains.kotlin:*` e `org.projectlombok:lombok`

**Storage**: N/A

**Testing**: `./mvnw clean verify` (suíte existente, sem testes novos)

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: Edição de `pom.xml` em 2 serviços backend Maven independentes

**Performance Goals**: Redução de tempo de build (efeito colateral esperado, não medido
formalmente nesta feature)

**Constraints**: Nenhuma mudança de comportamento em runtime; nenhum arquivo `.java`
alterado; preservar Independência dos Serviços (Princípio I) — cada `pom.xml` editado
isoladamente

**Scale/Scope**: 2 arquivos (`payment-api/pom.xml`, `invoice-api/pom.xml`) alterados; 0
arquivos de código de aplicação alterados

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Cada `pom.xml` editado de forma independente e idêntica em intenção; nenhum acoplamento novo entre módulos. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Remove configuração morta que não sustenta nenhum fluxo técnico real — reduz ruído sem afetar funcionalidade. **PASS** |
| III–VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: sem mudança de escopo — segue **PASS**. Não há
data-model/contracts (feature não introduz entidades nem interfaces).

## Project Structure

### Documentation (this feature)

```text
specs/002-remove-kotlin-lombok/
├── plan.md              # This file
├── research.md          # Phase 0 output — mapeamento exato dos blocos a remover
├── spec.md
├── quickstart.md          # Como reproduzir a verificação
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md` nem `contracts/` — feature não introduz entidades de dados nem
interfaces externas.

### Source Code (repository root)

```text
payment-api/pom.xml   # editar: remover blocos Kotlin/Lombok (ver research.md)
invoice-api/pom.xml   # editar: remover os mesmos blocos, independentemente
```

Nenhum arquivo em `src/` é alterado.

**Structure Decision**: Edição pontual de 2 arquivos de configuração de build, sem mudança
de estrutura de diretórios.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
