# Implementation Plan: Deploy do Backend de Observabilidade (Collector + Prometheus + Loki + Jaeger + Grafana)

**Branch**: `020-observability-stack` | **Date**: 2026-10-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-observability-stack/spec.md`

## Summary

Implantar um backend de observabilidade self-hosted desacoplado da aplicação: um
**OpenTelemetry Collector genérico** (distribuição **core**, não contrib — nenhum exporter
proprietário) recebe OTLP dos 3 serviços e roteia para **Jaeger** (traces), **Prometheus**
(métricas) e **Loki** (logs), todos unificados via **Grafana**. Esta é a segunda versão desta
feature — a primeira (`020-signoz-deploy`, descartada) mandava o SigNoz; a emenda à constitution
(v4.0.0, Princípio VI, decisão explícita do responsável pelo laboratório) trocou o requisito
para evitar lock-in de ferramenta: nenhum serviço MUST exportar telemetria direto para um
produto específico, só para o Collector genérico.

A decisão técnica central, confirmada por pesquisa (ver [research.md](./research.md)): como
Jaeger, Prometheus e Loki **já aceitam ingestão OTLP nativamente**, o Collector não precisa da
distribuição **contrib** nem de exporters dedicados (`prometheusremotewrite`, `loki`) — a
distribuição **core** (`otlp`/`otlphttp` apenas) basta para as 3 pipelines (traces, métricas,
logs), cada uma apontando para o endpoint OTLP nativo do backend correspondente. Isso reduz
superfície (imagem menor, sem exporters que a aplicação não usa) e reforça o próprio objetivo de
evitar lock-in: o contrato entre Collector e backend é OTLP puro dos dois lados.

## Technical Context

**Language/Version**: Java 21 (3 serviços) + infraestrutura (Docker Compose, manifests k8s)

**Primary Dependencies** (novas, só em infraestrutura — nenhuma dependência Java nova além do
`opentelemetry-logback-appender-1.0`, já trazido transitivamente por
`spring-boot-starter-opentelemetry` desde o item 1.12, a confirmar em research.md): imagens
pinadas (confirmadas via Docker Hub em 2026-10-12): `otel/opentelemetry-collector:0.162.0`
(core, **não** `-contrib`), `jaegertracing/all-in-one:1.76.0`, `prom/prometheus:v3.13.4`,
`grafana/loki:3.7.8`, `grafana/grafana:13.2.3`.

**Storage**: Jaeger (traces, storage in-memory do all-in-one — aceitável para laboratório,
dados não precisam sobreviver a restart), Prometheus (métricas, TSDB local), Loki (logs,
filesystem local) — nenhum reaproveita `postgres-api` nem os bancos dos 3 serviços (Princípio
III, isolamento por dono).

**Testing**: nenhum teste automatizado novo nos 3 serviços — validação via `quickstart.md`
(inspeção do Grafana) e confirmação de que a suíte completa existente (unitários, Controller,
integração HTTP/Testcontainers, mensageria — itens 1.6-1.12 e E2.1-E2.6) continua passando sem
alteração.

**Target Platform**: Docker Compose local (`infra/docker-compose.yml`) e cluster k8s local
(`infra/k8s/`)

**Project Type**: infraestrutura (5 componentes novos) + edição simétrica de configuração nos 3
serviços backend (só o endpoint OTLP muda de destino — continua sendo o Collector, agora
genérico em vez de embutido num produto)

**Performance Goals**: N/A (backend de observabilidade, não caminho crítico da API)

**Constraints**: contrato HTTP e comportamento de mensageria Kafka MUST permanecer idênticos
(FR-007); os 3 serviços MUST continuar funcionando sem o backend disponível (FR-008, graceful
degradation já garantida pelo item 1.12); nenhum exporter proprietário no Collector (FR-002);
trocar um componente do backend MUST ser transparente para os 3 serviços (FR-009); nenhum
serviço gerenciado proprietário de nuvem (FR-010, Princípio IV); 5 componentes novos de infra
somados a Kafka/Postgres/3 serviços/frontend podem exigir sequenciamento manual na máquina de
8 GB (mesmo padrão do item E2.6).

**Scale/Scope**: infraestrutura nova (Collector + Jaeger + Prometheus + Loki + Grafana em
Compose + k8s, Grafana com datasources provisionados como código) + 3 × (`application.properties`
editado — endpoint OTLP aponta para o Collector genérico em vez do default `localhost:4318` +
`logback-spring.xml` novo + 1 bean Java novo para instalar o appender OTLP de logs) — nenhuma
mudança em `Controller`/`Service`/`Repository`/`Model`/testes existentes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Configuração OTLP (endpoint + appender de log) replicada de forma independente nos 3 serviços, sem lib/config compartilhada — mesmo padrão do item 1.12. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Telemetria real chegando a backends reais (Jaeger/Prometheus/Loki), não simulada. **PASS** |
| III. Persistência Real | Sim | Cada backend usa seu próprio armazenamento, isolado dos 3 bancos de domínio — nenhum acesso cruzado. **PASS** |
| IV. Portabilidade Real para Qualquer Nuvem | Sim | Todo o backend é self-hosted (Compose + k8s), sem serviço gerenciado proprietário. **PASS** |
| VI. Observabilidade como Requisito de Primeira Classe (v4.0.0) | Sim | Esta feature **é** a implementação direta da redação atual do Princípio VI: Collector genérico desacoplando a aplicação do backend, com Prometheus/Loki/Jaeger/Grafana como a escolha vigente, não travada (FR-009/SC-006 provam isso diretamente). **PASS** |
| V, VII | Não aplicável | Feature não toca IAM nem agentes de SRE autônomos. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: nenhuma mudança de contrato HTTP; nenhum acesso cruzado de
schema; Collector configurado só com exporters genéricos (`otlp`/`otlphttp`), confirmando
FR-002/FR-009 — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/020-observability-stack/
├── plan.md              # This file
├── research.md          # Phase 0 output — decisões técnicas (Collector core, OTLP nativo, logs)
├── quickstart.md         # Phase 1 output — validação manual via Grafana
├── spec.md
├── checklists/requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md`/`contracts/` — nenhuma entidade de domínio nova, nenhuma mudança de
contrato HTTP (mesmo padrão do item 012-otel-basic-instrumentation).

### Source Code (repository root)

```text
infra/docker-compose.yml                    # editado: novos serviços otel-collector, jaeger,
                                             # prometheus, loki, grafana (+ config files),
                                             # mesma lab-network; editados: order-service/
                                             # payment-service/invoice-service ganham a env var
                                             # do endpoint OTLP apontando para o otel-collector
infra/otel-collector-config.yaml            # novo: pipelines traces/metrics/logs → otlp/otlphttp
infra/prometheus.yml                        # novo: scrape config (actuator) + otlp receiver flag
infra/grafana/provisioning/datasources/*.yaml  # novo: Prometheus/Loki/Jaeger provisionados como código

infra/k8s/otel-collector-*.yaml             # novo: ConfigMap + Deployment + Service
infra/k8s/jaeger-*.yaml                     # novo: Deployment + Service
infra/k8s/prometheus-*.yaml                 # novo: ConfigMap + Deployment + Service
infra/k8s/loki-*.yaml                       # novo: Deployment + Service
infra/k8s/grafana-*.yaml                    # novo: ConfigMap (datasources) + Deployment + Service
infra/k8s/*-deployment.yaml                 # editados: os 3 Deployments ganham a env var do
                                             # endpoint OTLP (Service do otel-collector em k8s)

order-api/src/main/resources/application.properties        # editado: endpoint OTLP do Collector
order-api/src/main/resources/logback-spring.xml             # novo: appender OTLP de logs (ecs)
order-api/src/main/java/.../config/OtlpLoggingConfig.java    # novo: instala o appender OTLP

payment-api/ (mesmo padrão: application.properties, logback-spring.xml, config/OtlpLoggingConfig.java)
invoice-api/ (mesmo padrão: application.properties, logback-spring.xml, config/OtlpLoggingConfig.java)
```

**Structure Decision**: infraestrutura ganha 5 componentes novos nos dois ambientes já
existentes (Compose/k8s), cada um como serviço/Deployment+Service próprio, mesma rede/namespace
— nenhum deles substitui ou reconfigura Postgres/Kafka/Kafbat já existentes. Nos 3 serviços,
nenhum pacote de domínio é tocado — só configuração de observabilidade, replicada de forma
independente (Princípio I).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada. A decisão de usar a
distribuição core do Collector (não contrib) está documentada no Summary e em research.md como
simplificação deliberada, não como violação de princípio.*
