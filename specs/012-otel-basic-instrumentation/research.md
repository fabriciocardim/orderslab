# Research: Instrumentação Básica com OpenTelemetry

## Decisão 1: `spring-boot-starter-opentelemetry` (não montar as peças na mão)

**Decision**: adicionar `org.springframework.boot:spring-boot-starter-opentelemetry` (sem
`<version>` — gerenciado pelo `spring-boot-dependencies:4.1.1`) aos 3 `pom.xml`.

**Rationale**: confirmado via `dependency:get` + inspeção do `.pom` resolvido que este
starter já traz tudo que a spec pede, transitivamente: `spring-boot-starter-micrometer-metrics`
(métricas), `spring-boot-micrometer-tracing-opentelemetry` (autoconfig da ponte Micrometer
Tracing → OTel), `spring-boot-opentelemetry` (autoconfig base do SDK OTel),
`micrometer-tracing-bridge-otel` (1.7.1), `micrometer-registry-otlp` (exporta métricas via
OTLP) e `opentelemetry-exporter-otlp` (exporta traces via OTLP) — versões geridas por
`micrometer-tracing.version=1.7.1` e pelo `testcontainers`/OTel core `1.62.0` do BOM. Mesmo
padrão de starter dedicado já visto no item 1.10 (`spring-boot-starter-flyway`) — Spring Boot
4.1.1 continua granularizando cada capacidade em um starter próprio em vez de exigir montar
peças soltas na mão.

**Alternatives considered**: adicionar `micrometer-tracing-bridge-otel` +
`opentelemetry-exporter-otlp` diretamente (sem o starter) — funcionaria, mas exigiria
descobrir e listar manualmente cada peça, contra a prática já estabelecida nesta sessão de
preferir o starter dedicado quando ele existe (menos superfície de erro de versão);
Java agent oficial do OTel (`opentelemetry-javaagent.jar`, anexado via `-javaagent`) —
rejeitado porque não é uma dependência Maven (exige baixar/gerenciar um jar fora do build,
mudar o comando de start em todo lugar que o serviço roda) e este projeto já usa o caminho
nativo do Spring Boot para tudo mais (actuator, JPA, Flyway) — manter consistência com esse
padrão em vez de introduzir uma segunda forma de instrumentar.

## Decisão 2: Sampling 100% (`management.tracing.sampling.probability=1.0`)

**Decision**: setar explicitamente `management.tracing.sampling.probability=1.0` nos 3
`application.properties`.

**Rationale**: o padrão do Spring Boot é 10% (`0.1`) — adequado para produção de alto
volume, mas inadequado para um laboratório de aprendizado onde o objetivo é *ver* o trace de
cada requisição manual durante o desenvolvimento (SC-001/SC-002 exigem que uma única
requisição já produza um trace visível). Ajustar sampling de verdade para volume de produção
é decisão de Fase 6 (junto com o Collector/SigNoz), não deste item.

## Decisão 3: Logs estruturados via `logging.structured.format.console` (nativo, sem lib nova)

**Decision**: setar `logging.structured.format.console=ecs` (Elastic Common Schema) nos 3
`application.properties`. Nenhuma dependência nova — confirmado via inspeção do jar
`spring-boot-4.1.1.jar` que o suporte a logging estruturado (formatos `ecs`/`logstash`/`gelf`
+ um formatador JSON genérico) é nativo do próprio `spring-boot` core desde a introdução
desse recurso, mantido em Boot 4.1.1 sem mudança de pacote.

**Rationale**: ECS é um formato JSON padrão de mercado já pensado para correlação com
tracing (campos `trace.id`/`span.id` fazem parte do schema). Quando Micrometer Tracing está
no classpath (via Decisão 1), o Spring Boot injeta automaticamente `traceId`/`spanId` no
contexto MDC do SLF4J durante uma span ativa — o formatador estruturado inclui esse contexto
no JSON de saída sem código adicional. **Validado empiricamente na implementação** (não só
por documentação): subir o serviço, disparar uma requisição, e confirmar via log real que o
JSON de saída contém `trace.id`/`span.id` preenchidos e coerentes com a requisição.

**Alternative considered**: `logstash` (outro formato suportado nativamente) — equivalente em
termos de correlação de trace; `ecs` escolhido por ser o mais amplamente adotado como padrão
neutro de schema de log estruturado no mercado de observabilidade.

## Decisão 4: Sem endpoint OTLP explícito — degradação graciosa por padrão

**Decision**: não configurar `management.otlp.tracing.endpoint`/
`management.otlp.metrics.export.url` explicitamente nesta feature — deixar o default do SDK
OTel (`http://localhost:4318`, protocolo OTLP/HTTP).

**Rationale**: sem nenhum Collector rodando nesse endpoint (fora de escopo deste item — ver
Assumptions da spec), as tentativas de exportação falham em background, numa thread
assíncrona de batch export, sem bloquear requisições nem impedir o startup do serviço — é o
comportamento padrão documentado dos exporters OTLP do próprio SDK. **Validado
empiricamente**: subir o serviço sem nenhum Collector no ar, confirmar que ele inicia
normalmente e responde a requisições HTTP sem erro (FR-006/SC-003), aceitando que logs de
tentativa de export falha apareçam em background (esperado, não é uma falha do serviço).

## Decisão 5: Métricas via `/actuator/metrics` — expor explicitamente

**Decision**: adicionar `management.endpoints.web.exposure.include=health,metrics` aos 3
`application.properties` (hoje só `health` é exposto via web, o padrão do Spring Boot).

**Rationale**: o starter da Decisão 1 já traz o registry de métricas (`micrometer-registry-otlp`)
e a autoconfiguração de métricas HTTP (`http.server.requests` etc.) — falta só expor o
endpoint `/actuator/metrics` via web, que por padrão vem desligado (só `health` é exposto).
Mínimo necessário para satisfazer FR-005, sem abrir mais endpoints do que o pedido.

## Decisão 6: Logs de transição de estado via SLF4J fluente com key-values

**Decision**: cada `Service` ganha um `Logger` (SLF4J, já no classpath via
`spring-boot-starter-logging`, transitivo de `spring-boot-starter`) e chama
`log.atInfo().addKeyValue(...).log(...)` nos pontos de `create`/`confirm`/`cancel` (e
`issue` em invoice-api) — a API fluente do SLF4J 2.x que os formatadores estruturados do
Spring Boot promovem automaticamente a campos JSON separados (não concatenados na mensagem).

**Rationale**: nenhum serviço tinha qualquer logging até agora (confirmado via grep — zero
uso de `Logger`/`LoggerFactory` no código atual) — esta feature introduz o primeiro uso,
direto no formato certo desde o início, em vez de logar texto plano e precisar migrar depois.
Key-values (ex.: `orderId`, `status`) viram campos JSON nativos, não texto interpolado —
mais fácil de filtrar/consultar quando um backend real existir (Fase 6).

## Conclusão

Por serviço: 1 dependência nova no `pom.xml` (`spring-boot-starter-opentelemetry`, sem
versão explícita), 4 propriedades novas em `application.properties`
(`management.tracing.sampling.probability=1.0`, `logging.structured.format.console=ecs`,
`management.endpoints.web.exposure.include=health,metrics` — mais nenhuma mudança de
`spring.datasource.*`/Flyway/Kafka já existentes), e o `Service` de cada serviço ganha um
`Logger` com chamadas estruturadas nos pontos de transição de estado. Nenhuma mudança de
contrato HTTP, nenhum Collector/backend implantado.
