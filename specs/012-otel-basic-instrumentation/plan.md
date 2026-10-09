# Implementation Plan: Instrumentação Básica com OpenTelemetry

**Branch**: `012-otel-basic-instrumentation` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-otel-basic-instrumentation/spec.md`

## Summary

Instrumentar os 3 serviços com `spring-boot-starter-opentelemetry` (traces + métricas via
OTLP, sampling 100%), ativar logging estruturado nativo do Spring Boot (`ecs`, com
trace_id/span_id correlacionados automaticamente via MDC) e adicionar logging explícito nos
pontos de transição de estado de cada `Service`. Nenhum Collector/backend implantado — os
exporters OTLP apontam para o default (`localhost:4318`), falhando em background sem afetar o
funcionamento do serviço. Ver [research.md](./research.md) para as 6 decisões técnicas,
incluindo a confirmação empírica do artifact correto (`spring-boot-starter-opentelemetry`,
mesmo padrão de starter dedicado já visto no item 1.10/Flyway).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies** (novas nos 3 serviços): `spring-boot-starter-opentelemetry`
(gerenciado pelo BOM `spring-boot-dependencies:4.1.1`, traz transitivamente
`micrometer-tracing-bridge-otel:1.7.1`, `opentelemetry-exporter-otlp:1.62.0`,
`micrometer-registry-otlp`, autoconfig de métricas/tracing)

**Storage**: N/A — feature não toca persistência

**Testing**: nenhum teste novo — validação é via `quickstart.md` (inspeção manual de logs) e
confirmação de que os 66 testes existentes (itens 1.6-1.11) continuam passando sem alteração

**Target Platform**: JVM (mesmo ambiente atual)

**Project Type**: 3 serviços backend — edição simétrica em cada um

**Performance Goals**: N/A

**Constraints**: contrato HTTP MUST permanecer idêntico (FR-007); ausência de Collector MUST
NOT impedir funcionamento normal do serviço (FR-006); os 3 serviços MUST usar exatamente o
mesmo mecanismo de instrumentação (FR-008, Princípio I)

**Scale/Scope**: 3 × (`pom.xml` editado + `application.properties` editado + `Service.java`
editado) = 9 arquivos tocados, nenhum arquivo novo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Mesmo padrão de instrumentação replicado 3x, sem lib/config compartilhada entre serviços. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Traces/logs/métricas reais gerados pela aplicação (não simulados), mesmo sem backend ainda — consistente com "real, não meio-termo". **PASS** |
| VI. Observabilidade como Requisito de Primeira Classe | Sim | Esta feature É o gancho mínimo explicitamente previsto pelo Princípio VI desde a v3.1.0/3.2.0 da constitution (padrão OpenTelemetry) — cobre os 3 pilares (logs, métricas, traces) do lado da aplicação, sem ainda exigir o SigNoz (isso é Fase 6, também já mandatado pela constitution). **PASS** |
| III, IV, V, VII | Não aplicável | Feature não toca persistência, portabilidade de infra, IAM nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: nenhuma mudança de contrato HTTP; nenhum Collector/backend
implantado (mantendo o escopo restrito ao que o Princípio VI exige "desde o início", sem
antecipar a Fase 6) — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/012-otel-basic-instrumentation/
├── plan.md              # This file
├── research.md          # Phase 0 output — 6 decisões técnicas
├── quickstart.md         # Phase 1 output — validação manual de traces/logs/métricas
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md`/`contracts/` — nenhuma entidade nova, nenhuma mudança de contrato HTTP.

### Source Code (repository root)

```text
order-api/pom.xml                                                    # editado (1 dep nova)
order-api/src/main/resources/application.properties                  # editado (4 props novas)
order-api/src/main/java/.../service/OrderService.java                 # editado (Logger + log em create/confirm/cancel)

payment-api/ (mesmo padrão: pom.xml, application.properties,
  service/PaymentService.java — Logger em create/confirm/cancel)

invoice-api/ (mesmo padrão: pom.xml, application.properties,
  service/InvoiceService.java — Logger em create/issue/cancel)
```

**Structure Decision**: nenhum pacote/arquivo novo — a mudança é inteiramente dentro dos
arquivos já existentes; `Controller`/`Repository`/`Model`/migrations/testes permanecem
intocados.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
