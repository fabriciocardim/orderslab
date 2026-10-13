# Tasks: Deploy do Backend de Observabilidade (Collector + Prometheus + Loki + Jaeger + Grafana)

**Input**: Design documents from `/specs/020-observability-stack/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: nenhum teste automatizado novo — validação é via `quickstart.md` (inspeção manual
via Grafana) e reconfirmação de que os testes existentes nos 3 serviços (itens 1.1-1.12,
E2.1-E2.6) continuam passando sem alteração de comportamento.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational — não aplicável

Nenhum pré-requisito externo bloqueante além do que os itens 1.1-1.12 já entregaram
(instrumentação OTLP básica, hoje sem destino real).

---

## Fase 3: User Story 1 - Telemetria real navegável via Grafana no ambiente local (Priority: P1) 🎯 MVP

**Goal**: Collector genérico + Jaeger + Prometheus + Loki + Grafana sobem via Docker Compose; os
3 serviços exportam OTLP (traces, métricas, logs) para o Collector; a telemetria de uma
requisição real fica navegável no Grafana.

**Independent Test**: subir o Compose completo, fazer uma requisição HTTP real a qualquer um
dos 3 serviços e confirmar, via Grafana (Explore), o trace (Jaeger), o log correlacionado
(Loki) e as métricas básicas (Prometheus) dessa requisição — ver `quickstart.md`.

### Implementation for User Story 1 — backend de observabilidade (infra)

- [X] T001 [P] [US1] Criar `infra/otel-collector-config.yaml`: receiver `otlp` (gRPC 4317/HTTP
      4318), processor `batch`, 3 pipelines de exporter genérico apontando para os backends
      nativos — `otlp` para o Jaeger (`jaeger:4317`), `otlphttp` para o Prometheus
      (`http://prometheus:9090/api/v1/otlp/v1/metrics`) e `otlphttp` para o Loki
      (`http://loki:3100/otlp`) — nenhum exporter proprietário (`prometheusremotewrite`/`loki`)
      (research.md, Decisão 1)
- [X] T002 [P] [US1] Criar `infra/prometheus.yml` mínimo (sem scrape configs — ingestão é só via
      OTLP) e adicionar o serviço `prometheus` (`prom/prometheus:v3.13.4`) a
      `infra/docker-compose.yml`, com `--web.enable-otlp-receiver` nos `command`, porta `9090`,
      na `lab-network` (research.md, Decisão 3)
- [X] T003 [P] [US1] Adicionar o serviço `jaeger` (`jaegertracing/all-in-one:1.76.0`) a
      `infra/docker-compose.yml`: portas `4317`/`4318` (OTLP) e `16686` (UI), env
      `COLLECTOR_OTLP_ENABLED=true`, storage in-memory (padrão), `lab-network` (research.md,
      Decisão 2)
- [X] T004 [P] [US1] Adicionar o serviço `loki` (`grafana/loki:3.7.8`) a
      `infra/docker-compose.yml`: porta `3100`, configuração padrão em modo monolítico
      (filesystem local), `lab-network` (research.md, Decisão 4)
- [X] T005 [P] [US1] Criar `infra/grafana/provisioning/datasources/datasources.yaml` com os 3
      datasources (Prometheus `http://prometheus:9090`, Loki `http://loki:3100`, Jaeger
      `http://jaeger:16686`) e adicionar o serviço `grafana` (`grafana/grafana:13.2.3`) a
      `infra/docker-compose.yml`: porta `3001` (3000 já é o frontend do laboratório), volume
      apontando `provisioning/` montado, `depends_on` prometheus/loki/jaeger, `lab-network`
      (research.md, Decisão 6)
- [X] T006 [US1] Adicionar o serviço `otel-collector` (`otel/opentelemetry-collector:0.162.0`,
      **não** `-contrib`) a `infra/docker-compose.yml`: monta `otel-collector-config.yaml`
      (T001), portas `4317`/`4318` expostas na rede interna, `depends_on` jaeger/prometheus/loki,
      `lab-network` (depende de T001-T004)

### Implementation for User Story 1 — order-api

- [X] T007 [P] [US1] `order-api`: editar `application.properties` —
      `management.opentelemetry.tracing.export.otlp.endpoint=http://otel-collector:4318/v1/traces`
      (corrigido durante a implementação: `management.otlp.tracing.endpoint` não existe no Boot
      4.1 — é `management.opentelemetry.tracing.*` para o starter novo, confirmado via
      `docs.spring.io/spring-boot/appendix/application-properties`; exportação de traces só
      funcionou após a correção),
      `management.otlp.metrics.export.url=http://otel-collector:4318/v1/metrics`,
      `management.opentelemetry.logging.export.otlp.endpoint=http://otel-collector:4318/v1/logs`
      (substitui o default `localhost:4318` do item 1.12)
- [X] T008 [P] [US1] `order-api`: criar `src/main/resources/logback-spring.xml` configurando o
      `OpenTelemetryAppender` (mantendo o encoder ECS já usado no console) — confirmar via
      inspeção do `.pom` resolvido se `opentelemetry-logback-appender-1.0` já vem transitivo de
      `spring-boot-starter-opentelemetry` (research.md, Decisão 5)
- [X] T009 [US1] `order-api`: criar
      `src/main/java/com/orderslab/order_api/config/OtlpLoggingConfig.java` chamando
      `OpenTelemetryAppender.install(openTelemetry)` no startup (depende de T008)
- [X] T010 [US1] `order-api`: adicionar o serviço `order-service` já existente em
      `infra/docker-compose.yml` à `env` com
      `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces` (e equivalentes de
      métricas/logs) — ou confirmar que o `application.properties` de T007 já resolve sem
      necessidade de env var, dado que o hostname `otel-collector` é igual em todo o Compose
      (depende de T006, T007)

### Implementation for User Story 1 — payment-api

- [X] T011 [P] [US1] `payment-api`: mesmo padrão de T007 em `application.properties`
- [X] T012 [P] [US1] `payment-api`: mesmo padrão de T008 em `src/main/resources/logback-spring.xml`
- [X] T013 [US1] `payment-api`: criar
      `src/main/java/com/orderslab/payment_api/config/OtlpLoggingConfig.java` (depende de T012)

### Implementation for User Story 1 — invoice-api

- [X] T014 [P] [US1] `invoice-api`: mesmo padrão de T007 em `application.properties`
- [X] T015 [P] [US1] `invoice-api`: mesmo padrão de T008 em `src/main/resources/logback-spring.xml`
- [X] T016 [US1] `invoice-api`: criar
      `src/main/java/com/orderslab/invoice_api/config/OtlpLoggingConfig.java` (depende de T015)

### Validação da User Story 1

- [X] T017 [US1] Rodar `quickstart.md` (Passos 1-2 + verificações SC-001 a SC-004): subir o
      backend + os 3 serviços via Compose, disparar uma requisição real, confirmar trace no
      Jaeger, log correlacionado no Loki e métricas no Prometheus, todos navegáveis via Grafana
      (depende de T006, T009, T010, T013, T016)
- [X] T018 [US1] Rodar `quickstart.md` (verificação FR-008): parar o backend de observabilidade
      e confirmar que os 3 serviços continuam respondendo normalmente (depende de T017)
- [X] T019 [US1] Rodar `quickstart.md` (verificação SC-006): editar só
      `infra/otel-collector-config.yaml` (trocar o destino de um pipeline) e confirmar que
      nenhum dos 3 serviços precisou ser tocado para a troca ter efeito (depende de T017)

**Checkpoint**: backend de observabilidade operacional via Docker Compose; telemetria real dos
3 serviços navegável via Grafana; graceful degradation e desacoplamento de backend confirmados.

---

## Fase 4: User Story 2 - Telemetria real navegável no cluster k8s local (Priority: P2)

**Goal**: o mesmo backend (Collector + Jaeger + Prometheus + Loki + Grafana) implantado no
cluster k8s local, com os 3 serviços também apontando para o Collector em k8s.

**Independent Test**: aplicar os manifests em `infra/k8s/`, fazer uma requisição real a
qualquer um dos 3 serviços rodando em k8s e confirmar a mesma telemetria navegável via Grafana
(port-forward) — ver `quickstart.md`, Passo 3.

### Implementation for User Story 2 — manifests k8s

- [X] T020 [P] [US2] Criar `infra/k8s/otel-collector-configmap.yaml` (mesmo conteúdo de T001,
      com hostnames trocados pelos Services k8s equivalentes),
      `infra/k8s/otel-collector-deployment.yaml` e `infra/k8s/otel-collector-service.yaml`
- [X] T021 [P] [US2] Criar `infra/k8s/jaeger-deployment.yaml` e `infra/k8s/jaeger-service.yaml`
      (mesma imagem/portas de T003)
- [X] T022 [P] [US2] Criar `infra/k8s/prometheus-configmap.yaml` (equivalente a T002),
      `infra/k8s/prometheus-deployment.yaml` (com `--web.enable-otlp-receiver`) e
      `infra/k8s/prometheus-service.yaml`
- [X] T023 [P] [US2] Criar `infra/k8s/loki-deployment.yaml` e `infra/k8s/loki-service.yaml`
      (mesma imagem/porta de T004)
- [X] T024 [P] [US2] Criar `infra/k8s/grafana-configmap.yaml` (datasources equivalente a T005,
      hostnames dos Services k8s), `infra/k8s/grafana-deployment.yaml` (monta o ConfigMap de
      provisioning) e `infra/k8s/grafana-service.yaml`

### Implementation for User Story 2 — 3 serviços em k8s

- [X] T025 [US2] Editar `infra/k8s/order-service-deployment.yaml`,
      `infra/k8s/payment-service-deployment.yaml` e
      `infra/k8s/invoice-service-deployment.yaml`: adicionar env vars equivalentes às de T007
      apontando para o Service `otel-collector` em k8s (depende de T020)

### Validação da User Story 2

- [ ] T026 [US2] Rodar `quickstart.md` (Passo 3): aplicar `infra/k8s/`, confirmar via
      port-forward do Grafana e dos 3 serviços a mesma telemetria da US1 (depende de T020-T025)

**Checkpoint**: backend de observabilidade operacional tanto via Compose quanto via k8s, sem
nenhum serviço gerenciado proprietário de nuvem (Princípio IV).

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T027 [P] Rodar `./mvnw clean verify` nos 3 serviços — confirmar que os testes existentes
      (itens 1.1-1.12, E2.1-E2.6) continuam passando sem alteração de comportamento (SC-004/FR-007)
- [X] T028 [P] Atualizar o item E6.1 de `ROADMAP.md` como concluído (incluindo a nota de que a
      feature foi replanejada de SigNoz para Collector genérico + Prometheus/Loki/Jaeger/Grafana
      por emenda à constitution v4.0.0)
- [ ] T029 Commitar as edições dos 3 serviços e `specs/020-observability-stack/` na branch
      `feature/O001_observability_stack`

---

## Dependencies & Execution Order

- US1 (backend Compose + 3 serviços) é independente de US2 — mas US2 reaproveita o mesmo
  conteúdo de configuração já validado em US1 (Collector pipelines, datasources do Grafana),
  então US1 **MUST** ser concluída e validada primeiro na prática, mesmo sem dependência formal
  de fase.
- Dentro de US1: os 5 serviços de infra (T001-T006) são paralelizáveis entre si; dentro de cada
  bloco de serviço (order/payment/invoice-api), `application.properties` e `logback-spring.xml`
  são paralelizáveis, mas o bean Java (T009/T013/T016) depende do `logback-spring.xml` do mesmo
  serviço.
- Dentro de US2: os 5 blocos de manifests k8s (T020-T024) são paralelizáveis entre si; a edição
  dos 3 Deployments (T025) depende do ConfigMap/Service do Collector (T020).
- Polish depende de US1 e US2 completas.

## Implementation Strategy

### MVP First (User Story 1)

1. Subir o backend de observabilidade (T001-T006) via Compose.
2. Configurar os 3 serviços (T007-T016) para exportar ao Collector.
3. Validar (T017-T019): telemetria navegável, graceful degradation, troca de backend
   transparente.
4. **STOP and VALIDATE**: se os 3 sinais aparecem no Grafana e os 3 serviços sobrevivem sem o
   backend, o MVP está pronto.

### Incremental Delivery

1. US1 (T001-T019) → backend operacional via Compose, telemetria real navegável.
2. US2 (T020-T026) → mesmo backend operacional via k8s.
3. Polish (T027-T029) → suíte de testes reconfirmada, ROADMAP atualizado, commit.

## Notes

- Nenhuma mudança em `Controller`/`Service`/`Repository`/`Model`/testes existentes — contrato
  HTTP e comportamento de mensageria idênticos.
- Nenhum exporter proprietário no Collector (`otlp`/`otlphttp` apenas) — é o que garante FR-009/
  SC-006 (trocar backend sem reinstrumentar os serviços).
- Ao concluir esta feature, o item E6.1 do ROADMAP fica completo; os próximos itens da Fase 6
  (E6.2-E6.6: dashboards como código, alerting/SLOs, tracing distribuído completo via Kafka)
  ficam para features futuras.
