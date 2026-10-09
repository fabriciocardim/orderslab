# Implementation Plan: Build Health Check e Correção de Dependências

**Branch**: `001-build-health-check` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-build-health-check/spec.md`

## Summary

Verificar se `./mvnw clean verify` passa nos 3 serviços (`order-api`, `payment-api`,
`invoice-api`) e se os artifact IDs declarados nos `pom.xml` são coordinates reais do Spring
Boot 4.1.1. **Resultado da pesquisa (Fase 0)**: os 3 serviços já buildam com sucesso e todos
os artifact IDs são válidos — não há correção de dependência a fazer. Ver
[research.md](./research.md) para a investigação completa. O trabalho remanescente desta
feature é apenas documental: corrigir a spec (já feito) e atualizar `ROADMAP.md` para não
reabrir essa suspeita por engano numa fase futura.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (parent POM em cada serviço), Maven (Maven
Wrapper `./mvnw`); nenhuma dependência nova introduzida por esta feature

**Storage**: N/A — feature não envolve dados

**Testing**: Maven Surefire via `./mvnw clean verify` (testes existentes de cada serviço);
nenhum teste novo introduzido por esta feature

**Target Platform**: JVM (mesmo ambiente de build local usado hoje pelos 3 serviços)

**Project Type**: Verificação/documentação sobre 3 serviços backend Maven independentes
(sem novo código de aplicação)

**Performance Goals**: N/A

**Constraints**: Nenhuma mudança de comportamento em runtime; preservar a Independência dos
Serviços (Princípio I) — nenhuma mudança compartilhada entre os 3 pom.xml

**Scale/Scope**: 3 serviços verificados; 0 arquivos de código de aplicação alterados; 2
documentos atualizados (`spec.md`, `ROADMAP.md`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Cada serviço foi verificado isoladamente (`./mvnw clean verify` rodado 3x, um por diretório); nenhuma mudança introduz acoplamento entre módulos. **PASS** |
| II. Funcionalidade Técnica Real | Sim | O objetivo da feature é garantir que o build (um fluxo técnico básico) funciona de ponta a ponta antes de empilhar mais fases em cima. Confirmado. **PASS** |
| III–VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de justificativa em Complexity Tracking.

**Re-check pós-design (Fase 1)**: sem mudança de escopo desde a checagem inicial — segue
**PASS**. Não há design de código nesta feature (achado da Fase 0 eliminou a necessidade).

## Project Structure

### Documentation (this feature)

```text
specs/001-build-health-check/
├── plan.md              # This file
├── research.md          # Phase 0 output — investigação que resolveu a suspeita original
├── spec.md              # Corrigida com o achado da pesquisa
├── quickstart.md         # Como reproduzir a verificação
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Não há `data-model.md` nem `contracts/` — a feature não introduz entidades de dados nem
interfaces externas novas; é puramente uma verificação de build.

### Source Code (repository root)

Nenhuma mudança de código de aplicação. Os 3 serviços (`order-api/`, `payment-api/`,
`invoice-api/`) permanecem com a estrutura atual — a investigação confirmou que já estão
corretos.

**Structure Decision**: Sem alteração de estrutura. Esta feature é verificação + correção de
documentação (`spec.md`, `ROADMAP.md`), não uma mudança de código.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
