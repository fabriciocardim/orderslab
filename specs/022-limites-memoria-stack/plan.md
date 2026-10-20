# Implementation Plan: Limites de Memória da Stack

**Branch**: `022-limites-memoria-stack` (na branch git `feature/T001_teste_carga`) | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-limites-memoria-stack/spec.md`

## Summary

Limites de memória em todos os serviços do `infra/docker-compose.yml` e dos manifests `infra/k8s/`, com heap das JVMs
proporcional ao limite (`MaxRAMPercentage`), heap fixo no Kafka, teto de traces no Jaeger, retenção no Prometheus,
`memory_limiter` no Collector, rotação de log e `restart: unless-stopped` no Compose, e amostragem de traces / log de
SQL configuráveis por env (`TRACING_SAMPLING_PROBABILITY`, `SPRING_JPA_SHOW_SQL`). Orçamento e decisões em
[research.md](./research.md).

## Technical Context

**Language/Version**: YAML (Docker Compose, Kubernetes, OpenTelemetry Collector); nenhum código Java muda

**Primary Dependencies**: Docker Compose v2, Kubernetes (Docker Desktop), JVM 21 (`MaxRAMPercentage`), Jaeger all-in-one 1.76, Prometheus 3.13, OTel Collector core 0.162

**Storage**: N/A (Postgres só ganha limite)

**Testing**: `docker compose config` (validação), parse dos manifests k8s, `./mvnw clean verify` nos 3 serviços (sem regressão), verificação do heap efetivo (`-XX:+PrintFlagsFinal`)

**Target Platform**: VPS Linux 2 vCPU / 7,75 GB (Compose + Traefik); cluster k8s local

**Project Type**: infraestrutura

**Performance Goals**: soma dos limites ≤ 6,5 GB (SC-001)

**Constraints**: nenhum comportamento dos serviços muda além do log de SQL; amostragem padrão continua 100%

**Scale/Scope**: 12 serviços no Compose, 12 Deployments no k8s, 2 cópias da config do Collector

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — cada serviço recebe seus próprios limites/variáveis; nada compartilhado. |
| II. Funcionalidade técnica real | **OK** — limites reais do runtime; nenhum atalho. |
| III. Persistência real | **OK** — Postgres inalterado além do limite. |
| IV. Portabilidade | **OK** — mesma mudança refletida em Compose **e** k8s (exigência do princípio); só recursos padrão. |
| V. Segurança | **N/A**. |
| VI. Observabilidade | **OK** — contrato OTLP app→Collector inalterado; amostragem passa a ser configurável (padrão 100%); `memory_limiter` é processor core do Collector, sem exporter proprietário. |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **Declarado** — item T3 do complemento "Desempenho sob carga". |

**Pós-design**: sem violações; Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/022-limites-memoria-stack/
├── spec.md
├── plan.md
├── research.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
infra/
├── docker-compose.yml           # mem_limit, restart, logging, env de JVM/amostragem/show-sql
├── otel-collector-config.yaml   # + processor memory_limiter
└── k8s/
    ├── *-deployment.yaml        # resources.requests/limits + mesmas env vars
    └── otel-collector-configmap.yaml  # + memory_limiter (cópia da config acima)
README.md                        # orçamento de memória + recomendação de swap
```

**Structure Decision**: só infraestrutura e documentação; os 3 serviços não mudam.

## Complexity Tracking

Sem violações.
