---

description: "Task list — limites de memória da stack (T3)"
---

# Tasks: Limites de Memória da Stack

**Input**: Design documents from `/specs/022-limites-memoria-stack/`

**Prerequisites**: plan.md, spec.md, research.md

**Tests**: validação de configuração (`docker compose config`, parse YAML) + suíte dos 3 serviços sem regressão.

**Organization**: US1 = contenção (limites, heap, restart); US2 = telemetria limitada (Jaeger, Prometheus, Collector,
amostragem, show-sql, rotação de log).

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 Validar `infra/docker-compose.yml` atual com `docker compose -f infra/docker-compose.yml config -q` como
  linha de base.

## Phase 2: User Story 1 — Um componente sem memória não derruba a máquina (P1) 🎯 MVP

- [X] T002 [US1] `infra/docker-compose.yml`: `mem_limit` e `restart: unless-stopped` em todos os serviços conforme o
  orçamento de `research.md` (Decisão 1).
- [X] T003 [US1] `infra/docker-compose.yml`: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65` em order/payment/invoice e
  kafbat-ui; `KAFKA_HEAP_OPTS=-Xms512m -Xmx512m` no kafka.
- [X] T004 [P] [US1] `infra/k8s/*-deployment.yaml`: `resources.requests`/`limits` (mesmos limites; requests = metade) e
  as mesmas variáveis de JVM.
- [X] T005 [US1] Verificar o heap efetivo de uma JVM com limite de 768 MB (`java -XX:MaxRAMPercentage=65
  -XX:+PrintFlagsFinal` num container com `--memory 768m`).

## Phase 3: User Story 2 — A telemetria não cresce sem limite (P2)

- [X] T006 [US2] `infra/docker-compose.yml`: `MEMORY_MAX_TRACES=20000` no jaeger; `--storage.tsdb.retention.time=7d`
  no prometheus; âncora `x-logging` (`json-file`, `max-size: 10m`, `max-file: 3`) em todos os serviços.
- [X] T007 [US2] `infra/docker-compose.yml`: `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=${TRACING_SAMPLING_PROBABILITY:-1.0}`
  e `SPRING_JPA_SHOW_SQL=${SPRING_JPA_SHOW_SQL:-false}` nos 3 serviços.
- [X] T008 [P] [US2] `infra/otel-collector-config.yaml` e `infra/k8s/otel-collector-configmap.yaml`: processor
  `memory_limiter` (`check_interval: 1s`, `limit_percentage: 80`, `spike_limit_percentage: 25`) como primeiro processor
  dos 3 pipelines.
- [X] T009 [P] [US2] `infra/k8s/`: mesmas variáveis de amostragem/show-sql nos 3 Deployments, `MEMORY_MAX_TRACES` no
  jaeger e retenção no prometheus.

## Phase 4: Polish

- [X] T010 `docker compose config -q`, parse de todos os manifests k8s e soma dos limites ≤ 6,5 GB.
- [X] T011 `./mvnw clean verify` nos 3 serviços (sem regressão; nenhum código mudou).
- [X] T012 [P] README: orçamento de memória, variáveis novas e recomendação de swap; ROADMAP item T3; `research.md`
  com as validações.
