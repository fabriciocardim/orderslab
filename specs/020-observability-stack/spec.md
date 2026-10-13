# Feature Specification: Deploy do Backend de Observabilidade (Collector + Prometheus + Loki + Jaeger + Grafana)

**Feature Branch**: `020-observability-stack`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "Iniciar a Fase 6 do ROADMAP (Observabilidade) com o item E6.1: Deploy do backend de observabilidade self-hosted. Contexto: os 3 microsserviços (order-api, payment-api, invoice-api) já têm instrumentação básica OpenTelemetry desde o item 1.12 do ROADMAP (traces via Micrometer->OTel bridge, logging estruturado com traceId/spanId correlacionados, métricas via /actuator/metrics) — mas nenhum Collector OTLP ou backend de observabilidade está implantado ainda. Decisão explícita do responsável pelo laboratório (emenda à constitution, v4.0.0, Princípio VI): para evitar lock-in de ferramenta, toda telemetria MUST passar por um OpenTelemetry Collector genérico (distribuição core, vendor-neutral) antes de chegar a qualquer backend — nenhum serviço MUST exportar direto para um produto específico. O backend self-hosted escolhido é Prometheus (métricas) + Loki (logs) + Jaeger (traces), visualizados via Grafana. Escopo: subir esse Collector genérico + Prometheus + Loki + Jaeger + Grafana via Docker Compose e via manifests k8s no cluster local; apontar o endpoint OTLP dos 3 serviços para o Collector (nunca diretamente para um backend específico); provisionar os datasources do Grafana como código; validar empiricamente que a telemetria real chega aos 3 backends e é navegável via Grafana, sem mudar o comportamento HTTP/Kafka dos serviços. Fora de escopo: dashboards como código, alerting/SLOs, tracing distribuído completo ponta-a-ponta via Kafka, métricas customizadas além do item 1.12 (ficam para E6.2-E6.6). Declaração de adiantamento (exigida pela constitution): esta feature antecipa a Fase 6 antes das Fases 3 (Airflow), 4 (hardening k8s) e 5 (Keycloak) — decisão consciente do responsável pelo laboratório."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Telemetria real navegável via Grafana no ambiente local (Docker Compose) (Priority: P1)

Como desenvolvedor do laboratório, quero subir um backend de observabilidade self-hosted
(Collector genérico + Prometheus + Loki + Jaeger + Grafana) no ambiente local via Docker
Compose e ver os 3 serviços enviando telemetria real a ele, para que a instrumentação feita
desde o item 1.12 finalmente tenha um destino funcional — sem acoplar os serviços a um produto
de observabilidade específico.

**Why this priority**: é o núcleo desta feature — sem um Collector genérico recebendo e
roteando a telemetria para backends reais, traces/logs/métricas continuam existindo só em
teoria. Todas as fases seguintes de observabilidade (E6.2-E6.6) dependem deste backend estar de
pé, e a decisão de passar pelo Collector (em vez de apontar direto para um produto) é o que
evita o lock-in que motivou esta feature.

**Independent Test**: subir `infra/docker-compose.yml` com o Collector + Prometheus + Loki +
Jaeger + Grafana inclusos, fazer uma requisição real a qualquer um dos 3 serviços e confirmar,
via Grafana, a presença do trace dessa requisição (Jaeger), do log estruturado correspondente
(Loki) e das métricas básicas (Prometheus).

**Acceptance Scenarios**:

1. **Given** o Collector genérico subido via Docker Compose recebendo OTLP dos 3 serviços,
   **When** uma requisição HTTP é feita a qualquer um deles, **Then** o trace dessa requisição
   aparece navegável no Jaeger (via Grafana) em poucos segundos.
2. **Given** o trace de uma requisição visível, **When** essa requisição disparou uma transição
   de estado (criar/confirmar/cancelar/emitir), **Then** o log estruturado dessa transição
   aparece no Loki, correlacionado ao mesmo trace.
3. **Given** os 3 serviços publicando/consumindo eventos Kafka normalmente (Fase 2), **When** a
   telemetria passa a fluir pelo Collector, **Then** nenhum comportamento de publicação,
   consumo, retry ou DLT muda.
4. **Given** o backend de observabilidade implantado, **When** um componente do backend (ex.:
   o backend de traces) precisasse ser trocado por outro no futuro, **Then** essa troca MUST
   ser possível mexendo só na configuração do Collector — nenhum dos 3 serviços precisa ser
   reinstrumentado ou reconfigurado.

---

### User Story 2 - Telemetria real navegável no cluster k8s local (Priority: P2)

Como desenvolvedor do laboratório, quero o mesmo backend disponível no cluster k8s local via
manifests equivalentes, para que a solução não fique presa a um único ambiente de execução e
continue cloud-agnostic (Princípio IV).

**Why this priority**: depende do desenho validado na US1 (mesmo stack, outro ambiente de
implantação); o laboratório já mantém Compose e k8s em paridade para toda a infra existente
(Postgres, Kafka, Kafbat), então este item fecha essa mesma paridade para observabilidade.

**Independent Test**: aplicar os manifests do Collector + Prometheus + Loki + Jaeger + Grafana
no cluster k8s local, apontar os Deployments dos 3 serviços para o Collector em k8s e confirmar
telemetria navegável via Grafana, da mesma forma que na US1.

**Acceptance Scenarios**:

1. **Given** o backend implantado no cluster k8s local, **When** uma requisição HTTP é feita a
   qualquer um dos 3 serviços rodando em k8s, **Then** o trace, o log e as métricas dessa
   requisição aparecem navegáveis via Grafana.
2. **Given** o backend implantado tanto via Compose quanto via k8s, **When** comparados,
   **Then** nenhum dos dois ambientes depende de um serviço gerenciado proprietário de um
   provedor de nuvem específico.

---

### Edge Cases

- **Backend de observabilidade indisponível ou ainda não implantado**: os 3 serviços MUST
  continuar respondendo normalmente (graceful degradation já garantida pelo item 1.12) — esta
  feature muda o destino da telemetria, não introduz uma dependência rígida de disponibilidade.
- **Correlação entre os 3 sinais não é nativa**: ao contrário de um backend único que já
  correlaciona traces/logs/métricas automaticamente, aqui são 3 sistemas de armazenamento
  distintos (Jaeger, Loki, Prometheus) unificados só na camada de visualização (Grafana, via
  `trace_id` como label/campo comum) — essa correlação manual/configurada é um trade-off aceito
  da decisão de evitar lock-in (Princípio VI), não um defeito a corrigir nesta feature.
- **Máquina de desenvolvimento com recursos limitados**: 5 componentes novos de infra (Collector,
  Jaeger, Prometheus, Loki, Grafana) somados a Kafka/Postgres/3 serviços/frontend MAY exigir
  sequenciamento manual de serviços na máquina de 8 GB, mesmo padrão já adotado na suíte de
  mensageria (item E2.6).
- **Nenhum exporter proprietário no Collector**: a configuração do Collector MUST usar somente
  exporters genéricos de protocolo (`otlp`/`otlphttp`), nunca um exporter dedicado a um único
  fornecedor — é o que garante a troca de backend sem reinstrumentar os serviços (FR da seção
  abaixo).
- **Dashboards, alerting e tracing distribuído via Kafka**: aparecem na medida em que a
  infraestrutura básica permitir, mas não são objeto de validação desta feature — ficam para
  E6.2-E6.6.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O ambiente local MUST subir um OpenTelemetry Collector genérico (distribuição
  core, vendor-neutral) como parte de `infra/docker-compose.yml`, recebendo OTLP dos 3 serviços.
- **FR-002**: O Collector MUST rotear a telemetria recebida para os backends correspondentes —
  traces para o Jaeger, métricas para o Prometheus, logs para o Loki — usando exclusivamente
  exporters genéricos de protocolo (`otlp`/`otlphttp`), nunca um exporter proprietário de um
  único fornecedor.
- **FR-003**: O cluster k8s local MUST ter manifests equivalentes para Collector, Jaeger,
  Prometheus, Loki e Grafana em `infra/k8s/`, seguindo o padrão de implantação já usado pelos
  demais componentes de infra do laboratório.
- **FR-004**: Os 3 serviços (order-api, payment-api, invoice-api) MUST ter seu endpoint OTLP
  (configurado desde o item 1.12) apontado para o Collector genérico — nunca diretamente para
  Jaeger, Prometheus ou Loki.
- **FR-005**: O Grafana MUST ter os datasources (Prometheus, Loki, Jaeger) provisionados como
  código (arquivo de configuração versionado), sem exigir configuração manual via UI.
- **FR-006**: A telemetria gerada pelos 3 serviços MUST ficar visível e navegável via Grafana
  após uma requisição real — traces (Jaeger), logs estruturados correlacionados ao trace (Loki)
  e métricas básicas (Prometheus).
- **FR-007**: O contrato HTTP e o comportamento de mensageria Kafka (publicação, consumo, retry,
  DLT) dos 3 serviços MUST permanecer idênticos ao estado atual.
- **FR-008**: Os 3 serviços MUST continuar funcionando normalmente mesmo quando o backend de
  observabilidade está indisponível ou ainda não foi implantado.
- **FR-009**: Trocar qualquer componente do backend (ex.: o destino de traces) MUST exigir
  mudança só na configuração do Collector — nenhum dos 3 serviços MUST precisar ser
  reinstrumentado ou ter sua configuração de endpoint OTLP alterada.
- **FR-010**: Nenhum componente do backend MUST depender de um serviço gerenciado proprietário
  de um provedor de nuvem específico (Princípio IV) — todos self-hosted.
- **FR-011**: Esta feature MUST deixar registrado explicitamente que dashboards como código,
  alerting/SLOs, tracing distribuído completo ponta-a-ponta via headers Kafka e métricas
  customizadas além do que o item 1.12 já expõe são escopo de E6.2-E6.6, não desta feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma requisição HTTP real a qualquer um dos 3 serviços aparece como trace
  navegável no Jaeger (via Grafana) em até 30 segundos após a requisição.
- **SC-002**: Um log estruturado de transição de estado aparece no Loki correlacionado ao
  trace da requisição que o originou (mesmo `trace_id`), navegável via Grafana.
- **SC-003**: As métricas operacionais básicas já expostas desde o item 1.12 ficam visíveis no
  Prometheus/Grafana.
- **SC-004**: 100% dos testes automatizados existentes nos 3 serviços e na suíte de mensageria
  (itens 1.1-1.12 e E2.1-E2.6) continuam passando sem alteração, confirmando ausência de
  regressão de comportamento.
- **SC-005**: O backend fica operacional tanto via Docker Compose local quanto via o cluster
  k8s local.
- **SC-006**: Apontar o Collector para um backend diferente de traces/logs/métricas é possível
  só editando a configuração do Collector, sem tocar em nenhum dos 3 serviços — prova direta de
  que o lock-in foi evitado.

## Assumptions

- "Collector genérico" significa a distribuição **core** do OpenTelemetry Collector (sem
  exporters proprietários de terceiros bundlados) — suficiente porque Jaeger, Prometheus e Loki
  aceitam ingestão OTLP nativamente, dispensando exporters dedicados de um único fornecedor;
  isso MUST ser confirmado empiricamente durante o `/speckit-plan`/implementação.
- Dashboards como código, alerting/SLOs e tracing distribuído completo via headers Kafka ficam
  para os itens E6.2-E6.6 do ROADMAP, não fazem parte desta feature.
- Na máquina de desenvolvimento de 8 GB, subir os 5 componentes do backend junto com
  Kafka/Postgres pode exigir sequenciamento manual de serviços — mesmo padrão já documentado na
  suíte de mensageria do item E2.6.
- Esta feature antecipa a Fase 6 do ROADMAP antes das Fases 3 (Airflow), 4 (hardening k8s) e 5
  (Keycloak) serem concluídas — adiantamento consciente, declarado conforme a seção Governance
  da constitution.
- Nenhum dos componentes do backend recebe autenticação/exposição externa nesta feature — mesmo
  padrão hoje usado pelo Kafbat UI no laboratório (acesso local, sem IdP).
- Correlação entre traces/logs/métricas via Grafana (não um modelo de dado unificado nativo) é
  um trade-off aceito da decisão de evitar lock-in — não é tratado como defeito desta feature.
