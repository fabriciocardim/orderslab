# Research: Deploy do Backend de Observabilidade (Collector + Prometheus + Loki + Jaeger + Grafana)

## Decisão 1: Collector na distribuição **core**, não **contrib**

**Decision**: usar a imagem `otel/opentelemetry-collector:0.162.0` (distribuição **core** —
receivers/processors/exporters `otlp`/`otlphttp`/`batch` apenas), **não**
`otel/opentelemetry-collector-contrib`.

**Rationale**: Jaeger, Prometheus e Loki **já aceitam ingestão OTLP nativamente** (ver Decisões
2-4) — não é preciso nenhum exporter especializado de um único fornecedor
(`prometheusremotewrite`, `loki`), só o exporter genérico `otlp` (para o Jaeger, via gRPC) e
`otlphttp` (para Prometheus e Loki, cada um apontando para o próprio endpoint OTLP nativo do
backend). Isso é a aplicação mais literal possível do requisito do Princípio VI (v4.0.0):
"nenhum exporter proprietário de um único fornecedor" — a distribuição core nem contém esses
exporters especializados, então a própria escolha de imagem já impõe o vendor-neutral.

**Alternatives considered**: `otel/opentelemetry-collector-contrib` com os exporters
`prometheusremotewrite`/`loki` dedicados — rejeitada: desnecessária (os 3 backends já falam
OTLP nativo) e contraria o espírito de "nenhum exporter proprietário" ao introduzir
dependência de exporters mantidos fora do core, quando o protocolo genérico já resolve.

**Confirmado por pesquisa direta**: `prometheus.io/docs/guides/opentelemetry/` (receiver OTLP
nativo), `grafana.com/docs/loki/.../native_otlp_vs_loki_exporter` (recomendação oficial da
Grafana de preferir o endpoint OTLP nativo ao exporter `loki`, hoje deprecated), documentação
do Jaeger sobre ingestão OTLP nativa desde a 1.35.

## Decisão 2: Jaeger all-in-one com storage in-memory

**Decision**: `jaegertracing/all-in-one:1.76.0`, portas `4317`/`4318` (OTLP gRPC/HTTP) e
`16686` (UI, consumida também como datasource do Grafana), storage in-memory (padrão do
all-in-one).

**Rationale**: all-in-one é a distribuição oficialmente recomendada para ambientes locais/de
laboratório (não produção) — um único contêiner com collector+query+UI, sem exigir um backend
de storage externo (Cassandra/Elasticsearch, usados em produção). Para este laboratório, dados
de trace não precisarem sobreviver a um restart é aceitável (mesmo espírito do SigNoz anterior,
mas sem a complexidade do systemd-in-container — ver decisão equivalente descartada no
`020-signoz-deploy`).

**A confirmar empiricamente na implementação**: se a 1.76.0 ainda exige a env var
`COLLECTOR_OTLP_ENABLED=true` explicitamente ou já vem habilitada por padrão (documentação
encontrada mostra o flag em exemplos, mas não confirma se é default em versões recentes) —
incluir o flag por segurança até confirmar.

## Decisão 3: Prometheus com receiver OTLP nativo (não scrape)

**Decision**: `prom/prometheus:v3.13.4`, iniciado com a flag `--web.enable-otlp-receiver`,
recebendo métricas via `POST /api/v1/otlp/v1/metrics` — o Collector exporta para esse endpoint
via `otlphttp`, em vez do Collector expor um endpoint de scrape (`prometheus` exporter) ou o
Prometheus fazer scrape direto do `/actuator/prometheus` dos 3 serviços.

**Rationale**: manter o caminho único e uniforme "aplicação → OTLP → Collector → OTLP →
backend" para os 3 sinais (traces, métricas, logs), em vez de um caminho diferente só para
métricas (scrape). Reduz uma forma a mais de configuração e mantém o Collector como único ponto
de saída de telemetria da aplicação, reforçando FR-004 (os 3 serviços nunca falam com um backend
específico, só com o Collector).

**Alternatives considered**: scrape direto do `/actuator/prometheus`
(`management.endpoints.web.exposure.include` já expõe `health,metrics` desde o item 1.12, bastaria
adicionar o formato Prometheus) — rejeitada para manter uniformidade com o fluxo OTLP único;
permanece como alternativa simples caso o receiver OTLP nativo do Prometheus apresente algum
problema de maturidade na implementação.

## Decisão 4: Loki com endpoint OTLP nativo (não o exporter `loki` dedicado)

**Decision**: `grafana/loki:3.7.8`, Collector exportando via `otlphttp` apontando para
`http://loki:3100/otlp`.

**Rationale**: a própria documentação da Grafana recomenda o endpoint OTLP nativo em vez do
exporter dedicado `loki` (hoje marcado como *deprecated* nessa documentação) — exatamente a
mesma lógica da Decisão 1/3: usar só o contrato OTLP genérico, nunca uma via proprietária.

## Decisão 5: Logs dos 3 serviços via `OpenTelemetryAppender` (reaproveitado do planejamento anterior)

**Decision**: igual à decisão já pesquisada para o plano anterior (`020-signoz-deploy`): cada
serviço ganha um `logback-spring.xml` configurando o `OpenTelemetryAppender` + uma classe que
chama `OpenTelemetryAppender.install(openTelemetry)`, com
`management.opentelemetry.logging.export.otlp.endpoint` apontando para o Collector (não para o
Loki diretamente — o Collector é quem decide o destino final, Decisão 4).

**Rationale**: confirmado na documentação oficial do Spring Boot 4.1.1 que mesmo com
`spring-boot-starter-opentelemetry`, o export de logs via OTLP não é automático — exige
configuração manual do appender. Essa decisão não muda com a troca de backend: é sobre como a
aplicação fala com o Collector, não sobre qual produto recebe o log depois.

## Decisão 6: Grafana com datasources provisionados como código

**Decision**: `grafana/grafana:13.2.3`, com um arquivo `infra/grafana/provisioning/datasources/datasources.yaml`
versionado declarando os 3 datasources (Prometheus, Loki, Jaeger) — montado no contêiner via
volume, sem nenhuma configuração manual via UI.

**Rationale**: consistente com o padrão de infraestrutura como código já usado no laboratório
(migrations Flyway, manifests k8s versionados, convenção de evento/tópico documentada) — um
backend de observabilidade que exige passos manuais de clique para funcionar não seria
reproduzível nem auditável.

## Decisão 7: k8s — manifests simples (sem Helm), diferente da decisão anterior do SigNoz

**Decision**: cada componente (Collector, Jaeger, Prometheus, Loki, Grafana) ganha um
Deployment+Service próprio em `infra/k8s/`, no mesmo padrão plano (`kubectl apply`) de tudo o
resto do projeto — com ConfigMaps para as configurações (Collector pipelines, Prometheus scrape
config inexistente/otlp-only, Grafana datasources).

**Rationale**: diferente do stack anterior do SigNoz (que envolvia ClickHouse/Keeper/StatefulSets
complexos, justificando usar o Helm chart oficial como gerador de referência), nenhum dos 5
componentes aqui exige storage distribuído ou topologia complexa — todos rodam como um único
Pod com storage efêmero (aceitável para laboratório, mesma decisão da Decisão 2 para o Jaeger).
Isso elimina por completo a necessidade de sequer considerar Helm, simplificando a decisão que
era necessária no plano anterior.

## Decisão 8: Sequenciamento na máquina de 8 GB

**Decision**: nota operacional para `quickstart.md` — sem mudança de código: confirmar que os 5
componentes novos (leves individualmente — nenhum deles roda um banco analítico como
ClickHouse) sobem junto com Kafka/Postgres/3 serviços/frontend; se a máquina não aguentar tudo
de uma vez, subir backend de observabilidade primeiro, domínio depois (ou vice-versa).

**Rationale**: mesmo que cada componente seja leve (Prometheus/Loki/Jaeger/Grafana não têm o
footprint do ClickHouse do SigNoz), 5 contêineres novos somados ao que já existe ainda é
volume considerável para 8 GB — mantém o mesmo cuidado já documentado no item E2.6.

## Conclusão

Por serviço (order-api/payment-api/invoice-api): `application.properties` ganha 3 propriedades
(endpoint OTLP de traces, métricas e logs, todas apontando para o **mesmo** Collector genérico,
não mais para um produto específico); 1 arquivo novo (`logback-spring.xml`); 1 classe de
configuração nova. Em infraestrutura: 5 componentes novos (Collector core + Jaeger + Prometheus
+ Loki + Grafana), todos self-hosted, todos falando OTLP nativo entre si — nenhum exporter
proprietário, nenhuma imagem privilegiada, nenhum Helm necessário.
