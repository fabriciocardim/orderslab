# Roadmap do orderslab

Backlog faseado do laboratório, alinhado à [constitution](.specify/memory/constitution.md)
(v3.0.0) e à seção "7. Fases de Evolução do Laboratório" do [README](README.md). Cada item
é candidato a virar uma feature isolada via `/speckit-specify` → `/speckit-plan` →
`/speckit-tasks` → `/speckit-implement`.

Este documento é um backlog de trabalho, não uma regra de governança — para princípios e
restrições do laboratório, ver a constitution. Para o "porquê" do laboratório existir, ver o
preâmbulo da constitution.

## Estado atual (confirmado por leitura direta do código, não só do README)

- `order-api`, `payment-api`, `invoice-api` têm o mesmo padrão: 5 endpoints CRUD-ish,
  armazenamento 100% em `ConcurrentHashMap`, sem validação de request, sem guarda de
  transição de estado, sem tratamento HTTP correto (um id inexistente retorna 500, não 404),
  sem teste real (só o `contextLoads()` vazio padrão).
- Zero Kafka de verdade (só a dependência + uma property), zero observabilidade além do
  `/actuator/health` default, zero footprint de Keycloak em código.
- Infra (`docker-compose`/k8s) já está à frente do código: Postgres e Kafka já sobem
  configurados e com env vars corretas apontando os 3 serviços — mas só `order-api` tem
  `spring-data-jpa`/driver Postgres no pom (comentados); `payment-api`/`invoice-api` nem têm
  essas dependências ainda.
- `payment-api`/`invoice-api` carregam um toolchain Kotlin+Lombok morto (zero uso real).
  Alguns artifact IDs no pom.xml (`spring-boot-starter-webmvc`, `spring-boot-starter-kafka`)
  são suspeitos e não foram confirmados por um build limpo.
- Os 3 serviços compartilham hoje uma única instância/DB Postgres (`apisdb`) sem decisão de
  schema-por-serviço.

## Fase 1 ✅ CONCLUÍDA (endurecer o que existe)

**Concluída em 2026-09-25** — todos os 12 itens (1.1-1.12) implementados e validados
empiricamente. Persistência real e validação deixaram de ser adiadas — ver Princípio III da
constitution (v3.0.0). Próximo passo: Fase 2 (Kafka assíncrono).

| # | Item | Descrição | Princípio | Arquivos/serviços |
|---|------|-----------|-----------|--------------------|
| 1.1 | ✅ Build health check | **Concluído (2026-09-24)** — `./mvnw clean verify` roda com `BUILD SUCCESS` nos 3 serviços. `spring-boot-starter-webmvc`, `spring-boot-starter-kafka` e os `-test` granulares (`-webmvc-test`, `-kafka-test`, `-actuator-test`) **são coordinates válidos do Spring Boot 4.1.1** (Boot 4 renomeou `web`→`webmvc`, criou starter oficial de Kafka, e passou a ter `-test` granular por starter em vez de um `spring-boot-starter-test` único) — nenhuma correção de dependência foi necessária. Ver [specs/001-build-health-check](specs/001-build-health-check/research.md). | I | `*/pom.xml` |
| 1.2 | ✅ Remover toolchain Kotlin/Lombok morto | **Concluído (2026-09-24)** — removidos `kotlin-stdlib`, `kotlin-test`, `kotlin-maven-plugin` (com sub-plugins) e `lombok` de `payment-api`/`invoice-api`; `./mvnw clean verify` continua `BUILD SUCCESS` nos 2 serviços, agora ~5x mais rápido (19s → 3.6s). Ver [specs/002-remove-kotlin-lombok](specs/002-remove-kotlin-lombok/research.md). | I, II | `payment-api/pom.xml`, `invoice-api/pom.xml` |
| 1.3 | ✅ Tratamento de erro HTTP — 404 real | **Concluído (2026-09-24)** — `@RestControllerAdvice` + `ErrorResponse` (record) implementados de forma independente nos 3 serviços, mapeando `*NotFoundException` → 404 com corpo consistente (`timestamp`, `status`, `error`, `message`, `path`). Validado empiricamente (boot + curl) nos 3 serviços; build e testes existentes continuam passando. Ver [specs/003-http-404-error-handling](specs/003-http-404-error-handling/research.md). | II | `*/exception/*NotFoundException.java`, `*/exception/GlobalExceptionHandler.java` |
| 1.4 | ✅ Validação de request | **Concluído (2026-09-24)** — `spring-boot-starter-validation` adicionado aos 3 serviços; `@NotBlank`/`@NotNull @Positive`/`@Pattern` (UUID) nos 3 DTOs; `@Valid` nos controllers; `ErrorResponse` (item 1.3) estendido com `validationErrors`. `orderId`/`paymentId` permanecem `String`, validados por formato via `@Pattern` (decisão: migrar tipo fica para a Fase de persistência). Validado empiricamente nos 3 serviços. Ver [specs/004-request-validation](specs/004-request-validation/research.md). | II | `*/dto/*Request.java`, controllers, `*/pom.xml`, `*/exception/*` |
| 1.5 | ✅ Máquina de estados / guardas de transição | **Concluído (2026-09-24)** — `confirm()`/`cancel()`/`issue()` só têm sucesso a partir do estado inicial de cada recurso (`PENDING`/`RESERVED`); reuso retorna `409` via `InvalidStatusTransitionException` (novo, por serviço) + `GlobalExceptionHandler`. Removidos os 4 valores de enum sem uso real: `OrderStatus.COMPLETED`, `PaymentStatus.PENDING` (achado durante a investigação) e `FAILED`, `InvoiceStatus.FAILED`. `payment-api` agora cria o pagamento já como `RESERVED`. Validado empiricamente nos 3 serviços. Ver [specs/005-state-transition-guards](specs/005-state-transition-guards/research.md). | II | `*/model/*Status.java`, `*/service/*Service.java`, `*/exception/*` |
| 1.6 | ✅ Testes unitários de Service | **Concluído (2026-09-24)** — `OrderServiceTest`/`PaymentServiceTest`/`InvoiceServiceTest` (10 testes cada, 30 no total), JUnit 5 + AssertJ, sem contexto Spring. Cobre criação, busca, 404, transições válidas/bloqueadas (item 1.5). Cobertura de Bean Validation (item 1.4) fica para o item 1.7 (testes de Controller), já que `@Valid` roda na camada MVC, antes do Service. Ver [specs/006-service-unit-tests](specs/006-service-unit-tests/research.md). | II | `*/src/test/java/.../service/*ServiceTest.java` |
| 1.7 | ✅ Testes de Controller (`@WebMvcTest`) | **Concluído (2026-09-24)** — `OrderControllerTest`/`PaymentControllerTest`/`InvoiceControllerTest` (9 testes cada, 27 no total), `@WebMvcTest` + `@MockitoBean` mockando o Service. Cobre 201/400 (criação válida/inválida), 200/404 (busca), 200 (listagem) e 200/409 (2 transições cada). Confirmado empiricamente: Spring Boot 4.1.1 removeu `@MockBean` (só `@MockitoBean`) e moveu `@WebMvcTest` de pacote; também migrou pra Jackson 3 (`tools.jackson.databind`), contornado usando JSON como String literal em vez de `ObjectMapper`. 60 testes passando nos 3 serviços (30 do item 1.6 + 27 novos + 3 `contextLoads` originais). Ver [specs/007-controller-tests](specs/007-controller-tests/research.md). | II | `*/src/test/java/.../controller/*ControllerTest.java` |
| 1.8 | ✅ Teste de integração HTTP baseline | **Concluído (2026-09-25)** — os 3 `*ApplicationTests.java` reescritos com `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@AutoConfigureRestTestClient`, exercitando criar→transicionar→buscar via HTTP real (servidor real, sem mocks). Usa `RestTestClient` (novo em Spring Framework 7 — confirmado via bytecode como o substituto atual do `TestRestTemplate`, que só moveu de pacote). 66 testes passando nos 3 serviços (itens 1.6+1.7+1.8 combinados). Ver [specs/008-http-integration-baseline](specs/008-http-integration-baseline/research.md). | II | `*/src/test/java/.../*ApplicationTests.java` |
| 1.9 | ✅ Decisão de ownership de schema/DB | **Concluído (2026-09-25)** — decisão: 1 instância Postgres compartilhada, mas 3 bancos + 3 usuários dedicados (`order_db`/`order_user`, `payment_db`/`payment_user`, `invoice_db`/`invoice_user`), provisionados via `infra/postgres-init/init-databases.sql` (mecanismo `/docker-entrypoint-initdb.d/`). Validado empiricamente em Docker Compose **e** no cluster k8s local: cada usuário só acessa o próprio banco (`permission denied` nos outros). Achado real durante a implementação: `CREATE DATABASE` concede `CONNECT` a `PUBLIC` por padrão — corrigido com `REVOKE CONNECT ... FROM PUBLIC` explícito, sem o qual o isolamento não funcionava de verdade. Env vars dos 3 serviços (Compose + k8s) já atualizadas para o item 1.10 não precisar mexer em infra de novo. Ver [specs/009-db-ownership-decision](specs/009-db-ownership-decision/research.md). | I, III | `infra/docker-compose.yml`, `infra/postgres-init/init-databases.sql`, `infra/k8s/postgres-api-*.yaml`, `infra/k8s/*-service-deployment.yaml` |
| 1.10 | ✅ Persistência JPA simétrica | **Concluído (2026-09-25)** — `spring-data-jpa`+`postgresql`+`spring-boot-starter-flyway`+`flyway-database-postgresql` nos 3 poms; `Order`/`Payment`/`Invoice` anotados `@Entity` diretamente (`EnumType.STRING`, sem `@GeneratedValue`); `*Repository extends JpaRepository<T, UUID>` injetado via construtor, substituindo os `ConcurrentHashMap`; migração Flyway (`V1__create_<tabela>_table.sql`) por serviço; `application.properties` ativado (`localhost:5432`, banco/usuário dedicado do item 1.9, `ddl-auto=validate`). Testes de Service (item 1.6) adaptados para Mockito (`@Mock`/`@InjectMocks`). Achado real durante a implementação: `flyway-core` cru **não é auto-configurado** em Spring Boot 4.1.1 — `FlywayAutoConfiguration` só é trazida pelo starter dedicado `spring-boot-starter-flyway` (mesmo padrão de granularização de `-webmvc`/`-kafka` do item 1.1); sem ele a app sobe normalmente mas a migração nunca roda, falhando silenciosamente com `SchemaManagementException: missing table`. Validado empiricamente: 66/66 testes passando nos 3 serviços com Postgres real; persistência sobrevive a restart real do processo (criar→matar→subir→buscar, mesmos dados) nos 3 serviços; schema recriado do zero automaticamente pela migração após `postgres-api` ser destruído e recriado (`flyway_schema_history` confirma). Ver [specs/010-jpa-persistence](specs/010-jpa-persistence/research.md). | II, III | `*/pom.xml`, `*/model/*.java`, novo `*/repository/*Repository.java`, novo `*/db/migration/V1__create_*_table.sql`, `*/application.properties`, `*/service/*Service.java`, `*/service/*ServiceTest.java` |
| 1.11 | ✅ Testes de integração com Testcontainers | **Concluído (2026-09-25)** — os 3 `*ApplicationTests.java` (item 1.8) passam a subir um Postgres efêmero via Testcontainers (`@Testcontainers`+`@Container`+`@ServiceConnection`), fechando a lacuna aceita no item 1.10 (FR-008): `mvnw test`/CI não depende mais de nenhuma infra do projeto previamente provisionada, só de um runtime Docker disponível. A mesma migração Flyway do item 1.10 roda contra o container efêmero — schema de teste 100% equivalente ao de produção, nenhum atalho `ddl-auto`. Achado real durante a implementação: **Testcontainers 2.x renomeou todos os módulos de banco/integração com o prefixo `testcontainers-`** (`postgresql`→`testcontainers-postgresql`, `junit-jupiter`→`testcontainers-junit-jupiter`) — os nomes "clássicos" usados em praticamente todo tutorial pré-2.x não resolvem mais (terceira surpresa de nomenclatura de artifact nesta sessão, depois de `-webmvc`/`-kafka` no item 1.1 e `spring-boot-starter-flyway` no item 1.10). `@ServiceConnection` manteve o mesmo pacote de sempre. Validado empiricamente com a infra do projeto (`docker compose`) totalmente parada: 66/66 testes passando nos 3 serviços, incluindo `./mvnw clean verify`; duas execuções consecutivas produzem resultado idêntico (sem vazamento de dado entre rodadas); testes de Service (1.6)/Controller (1.7) confirmados sem qualquer dependência de Docker. Ver [specs/011-testcontainers-integration](specs/011-testcontainers-integration/research.md). | II | `*/pom.xml`, `*/src/test/java/.../*ApplicationTests.java` |
| 1.12 | ✅ Instrumentação básica com OpenTelemetry | **Concluído (2026-09-25)** — `spring-boot-starter-opentelemetry` adicionado aos 3 poms (traces via Micrometer→OTel bridge + exporters OTLP, sampling 100%); logging estruturado nativo do Spring Boot (`logging.structured.format.console=ecs`), com `traceId`/`spanId` correlacionados automaticamente via MDC; cada `Service` ganhou um `Logger` (SLF4J, primeiro uso de logging no projeto) com chamadas estruturadas (`log.atInfo().addKeyValue(...)`) em create/confirm/cancel (e issue em invoice-api); métricas expostas via `/actuator/metrics` (`management.endpoints.web.exposure.include=health,metrics`). Nenhum Collector/SigNoz implantado — isso é Fase 6. Achado real: existe um `spring-boot-starter-opentelemetry` dedicado em Boot 4.1.1 (mesmo padrão de starter granular do item 1.10/Flyway), que já bundla tracing+métricas+exporters OTLP — não precisa montar as peças na mão. Validado empiricamente: serviço sobe e responde normalmente sem nenhum Collector rodando em `localhost:4318` (degradação graciosa confirmada); uma requisição real gerou um log estruturado com `traceId`/`spanId` idênticos entre o acesso HTTP e o log de transição de estado do `Service`; `/actuator/metrics/http.server.requests` populado; 66/66 testes dos itens 1.6-1.11 continuam passando sem alteração de comportamento. Ver [specs/012-otel-basic-instrumentation](specs/012-otel-basic-instrumentation/research.md). | VI | `*/pom.xml`, `*/application.properties`, `*/service/*Service.java` |

**Sequenciamento interno**: 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6/1.7/1.8 (trava de regressão
antes de mexer em storage) → 1.9 → 1.10 → 1.11 → 1.12.

## Fases 2–7 — epics de alto nível

### Fase 2 ✅ CONCLUÍDA — Kafka assíncrono
- **E2.1** ✅ Convenção de evento/tópico — **Concluído (2026-09-26)** — decisão documentada:
  1 tópico por tipo de evento (`<domínio>.<evento>`, minúsculas — ex.: `order.created`,
  `payment.reserved`, `invoice.issued`); envelope obrigatório em todo evento
  (`eventId`/`eventType`/`eventVersion`/`occurredAt`/`orderId`), com `orderId` exigido mesmo
  nos eventos de `payment-api`/`invoice-api` que não são diretamente sobre um pedido. Achado
  real: `spring-kafka:4.1.1` traz **duas famílias de serializer JSON** — a clássica
  (`JsonSerializer`/`JsonDeserializer`, Jackson 2) e a nova `JacksonJsonSerializer`/
  `JacksonJsonDeserializer` (Jackson 3, mesmo padrão de divisão já visto no item 1.7);
  escolhida a família Jackson 3, consistente com o resto do projeto. Zero dependência de
  código compartilhada entre os 3 serviços (Princípio I) — nenhum `pom.xml`/código de
  produção alterado por esta decisão. Ver
  [specs/013-kafka-event-convention](specs/013-kafka-event-convention/contracts/event-contract.md)
  para o contrato completo, consumido diretamente por E2.2. (I, II).
- **E2.2** ✅ `order-api` produtor — **Concluído (2026-10-04)** — `OrderCreated`/
  `OrderConfirmed`/`OrderCancelled` publicados em `order.created`/`order.confirmed`/
  `order.cancelled` (3 partições, chave `orderId`) via **outbox transacional** (`outbox_events`,
  Flyway `V2`): o evento é gravado na mesma transação da transição e um relay `@Scheduled`
  publica com ack e remove; entrega pelo menos uma vez com `eventId` estável. `confirm`/
  `cancel` leem o pedido com `FOR UPDATE` (Hibernate 7 emite `for no key update`), então
  transições concorrentes viram 200+409 com um único evento final, sem mudar o contrato HTTP.
  Validado contra Kafka/Postgres reais: broker parado/religado, `kill -9` entre persistir e
  publicar, confirm×cancel concorrente, tópicos sem header `__TypeId__`. Nenhuma dependência
  nova; `payment-api`/`invoice-api` intocados. 42 testes, PMD limpo. Ver
  [specs/014-order-kafka-producer](specs/014-order-kafka-producer/research.md). (II).
- **E2.3** ✅ `payment-api` consumidor+produtor — **Concluído (2026-10-04)** — consome
  `OrderCreated` (`order.created`) e publica `PaymentReserved`/`PaymentFailed`
  (`payment.reserved`/`payment.failed`, 3 partições, chave `orderId`). Regra de decisão simples e
  determinística: valor **estritamente acima de 1000.00** (`payment.approval.limit`) falha com
  `AMOUNT_LIMIT_EXCEEDED`; caso contrário reserva. O processamento de cada mensagem é **uma única
  transação** (dedup + pagamento + evento no outbox próprio, mesmo desenho do E2.2 copiado — sem
  código compartilhado, Princípio I). Idempotência por constraints em `payments`
  (`source_event_id` único e índice parcial único em `order_id`), sem tabela extra; pagamento
  novo valor `FAILED`, contrato HTTP inalterado. Achado: o error handler **padrão** do
  spring-kafka tenta 10× sem espera e **descarta** o registro (perderia decisões) — trocado por
  retry ilimitado com espera de 1 s para falha transitória; JSON ilegível é logado e pulado
  (retry limitado/DLT é o E2.5). Validado contra Kafka/Postgres reais: backlog lido
  (`earliest`), reentrega sem duplicar, ilegíveis, broker parado, `kill -9` entre persistir e
  publicar, Postgres parado sem perda, `traceId` no consumo. Nenhuma dependência nova;
  `order-api`/`invoice-api` intocados. 52 testes, PMD limpo. Ver
  [specs/015-payment-kafka-consumer-producer](specs/015-payment-kafka-consumer-producer/research.md). (II).
- **E2.4** ✅ `invoice-api` consumidor+produtor — **Concluído (2026-10-04)** — consome
  `PaymentReserved` (`payment.reserved`; `payment.failed` não é assinado, então pagamento que falhou
  não gera nota) e publica `InvoiceIssued`/`InvoiceFailed` (`invoice.issued`/`invoice.failed`,
  3 partições, chave `orderId`). Regra simples e determinística: valor **estritamente acima de
  500.00** (`invoice.issuance.limit`) falha com `AMOUNT_ABOVE_ISSUANCE_LIMIT`, sem imposto real; o
  limite é menor que o do pagamento (1000.00) de propósito, para exercitar a falha da nota ponta a
  ponta. Mesmo desenho validado no E2.3, **copiado** (terceira cópia do outbox, sem código
  compartilhado — Princípio I): uma transação por mensagem (dedup + nota + outbox), idempotência por
  constraints em `invoices`, retry ilimitado para falha transitória, ilegível logado e pulado. Novo
  status `FAILED`; contrato HTTP inalterado. Validado com os três serviços e Kafka/Postgres reais:
  cadeia completa (10.50/500 emitida, 750 pagamento ok + nota falha, 1500 sem nota), backlog lido,
  reentrega, ilegíveis, broker parado, `kill -9`, Postgres parado, `traceId` encadeado. Nenhuma
  dependência nova; `order-api`/`payment-api` intocados. 52 testes, PMD limpo. Ver
  [specs/016-invoice-kafka-consumer-producer](specs/016-invoice-kafka-consumer-producer/research.md). (II).
- **E2.5** ✅ Estratégia de erro de consumo — **Concluído (2026-10-04)** — `payment-api` (`order.created`) e
  `invoice-api` (`payment.reserved`), cada um com a sua cópia (Princípio I): `DefaultErrorHandler` com
  **backoff exponencial limitado** (padrão 4 retentativas, 1 s ×2 até 10 s; `consumer.retry.*`) e
  `DeadLetterPublishingRecoverer` para o **DLT `<tópico>.dlt`** (`order.created.dlt`,
  `payment.reserved.dlt`; 3 partições, chave preservada). Conteúdo inválido (`InvalidMessageException`) vai
  **direto** ao DLT sem retry (antes: log e descarte); falha transitória que persiste vai ao DLT sem bloquear
  a partição para sempre (antes: retry sem limite). DLT guarda chave/valor originais + headers
  `kafka_dlt-*`; se o envio ao DLT falhar a mensagem é reentregue. Achado: com o Hikari no padrão cada
  tentativa bloqueava ~30 s com o banco fora — `connection-timeout=5000` torna o ciclo ≈ 36 s por mensagem
  (medido). Validado em Kafka/Postgres reais: inválidas ao DLT sem retry, queda curta (sucesso na 3ª
  tentativa), queda longa (3 mensagens ao DLT sem perda e consumo retomando), reprocessamento do DLT sem
  duplicar, convenção registrada no contrato do E2.1. 57 testes por serviço, PMD limpo; `order-api` e `pom.xml`
  intocados. Parcial: "envio ao DLT falha" validado só em teste de unidade (sem Kafka nada chega ao serviço). Ver
  [specs/017-consumer-retry-dlt](specs/017-consumer-retry-dlt/research.md). (II).
- **E2.6** ✅ Testes de mensageria — **Concluído (2026-10-04)** — cada serviço ganhou uma suíte própria
  (`*MessagingTest`, sem código de teste compartilhado) que roda dentro de `./mvnw test` contra **Kafka
  (`apache/kafka:4.2.0`, a imagem do compose) e Postgres reais** subidos por Testcontainers (única mudança de
  `pom.xml`: `testcontainers-kafka`, escopo `test`, versão do BOM). `order-api`: 1 evento por operação no tópico
  certo, chave = `orderId`, sem `__TypeId__`, envelope, 3 partições, ordem criação→confirmação e **broker pausado**
  (HTTP normal, outbox retém, entrega ao retomar). `payment-api`/`invoice-api`: `OrderCreated`/`PaymentReserved` reais
  viram decisão + evento de saída (regra do limite, incluindo o valor exato), reentrega não duplica, mensagens
  inválidas vão ao DLT com chave/valor idênticos e headers `kafka_dlt-*` **sem retry** (provado por retry de teste de
  60 s) e a válida seguinte é processada. Validado: **10 execuções consecutivas verdes por serviço**, mutações
  (chave/tópico errados) detectadas com mensagem clara, `verify` e PMD limpos, nada em `src/main`. Achados: a
  entrega é pelo menos uma vez também nos testes (cópias com o mesmo `eventId` — passaram a contar eventos
  distintos); `max.block.ms=5000` de produção estoura na partida a frio do broker (o `publish` do teste insiste até
  um prazo); numa máquina de 8 GB com swap cheio o Kafka de teste falhou em subir — heap limitado e timeout de subida
  de 3 min. Ver [specs/018-messaging-testcontainers](specs/018-messaging-testcontainers/research.md). (II).

**Fase 2 ✅ CONCLUÍDA (2026-10-04)** — E2.1–E2.6: convenção de evento/tópico, `order-api` produtor (outbox),
`payment-api` e `invoice-api` consumidores+produtores, retry limitado + DLT e testes de mensageria com Kafka real.
A cadeia `order-api → payment-api → invoice-api` funciona de ponta a ponta de forma assíncrona. Próximo passo:
Fase 3 (Apache Airflow).

### Fase 3 — Apache Airflow
- **E3.1** Caso de uso do DAG (ex.: cancelar pedidos `PENDING` travados, reconciliação diária)
  (II).
- **E3.2** Deploy do Airflow — compose local + manifests k8s, sem features proprietárias de
  provedor (IV).
- **E3.3** Implementação do DAG com chamadas reais às APIs (II).
- **E3.4** Logs mínimos de execução do DAG — prepara terreno para Fase 6 (VI).

### Fase 4 — Kubernetes local (hardening do que já existe)
- **E4.1** Probes reais (liveness/readiness) usando `/actuator/health` já significativo (IV).
- **E4.2** Secrets externalizados — mover credenciais para `Secret` do k8s (IV).
- **E4.3** Revisão de namespace/network policy e estratégia de acesso local (IV).
- **E4.4** Kustomize/Helm — abstrair local vs "qualquer nuvem" sem duplicar manifests; fazer
  depois da Fase 2 para já capturar tópicos/consumer groups na mesma abstração (IV).

### Fase 5 — Keycloak
- **E5.1** Deploy do Keycloak — ativar bloco comentado do compose + k8s; realm/client
  provisionados como código, não clique manual (V, IV).
- **E5.2** Resource server nos 3 serviços — `spring-boot-starter-oauth2-resource-server`,
  validação de JWT, cada serviço protegendo seus próprios endpoints (V).
- **E5.3** Modelo simples de roles/scopes, mantendo domínio simples (V, II).
- **E5.4** Testes de integração autenticados — Testcontainers Keycloak (V, II).
- **E5.5** Questão em aberto: constitution menciona integração "ao frontend", mas não existe
  frontend no repo hoje — resolver escopo (Swagger UI autenticado? só service-to-service?).

### Fase 6 — Observabilidade (3 pilares, padrão OpenTelemetry → SigNoz)
O par instrumentação+backend já está decidido (OpenTelemetry → SigNoz, ver Princípio VI da
constitution) — SigNoz embute seu próprio OTel Collector e ClickHouse, dispensando montar uma
stack modular separada de Prometheus/Grafana/Loki/Tempo. O que resta decidir nesta fase é a
topologia de deploy.
- **E6.1** Deploy do SigNoz self-hosted — Docker Compose (`infra/docker-compose.yml`) para
  ambiente local e Helm chart em `infra/k8s/` para o cluster; apontar o endpoint OTLP do
  SigNoz nos 3 serviços (VI, IV).
- **E6.2** Métricas — auto-instrumentação OTel exportando via OTLP para o SigNoz nos 3
  serviços (VI).
- **E6.3** Logs estruturados centralizados — enriquecidos com `trace_id`/`span_id` (correlação
  automática do OTel) e id de entidade, visualizados no SigNoz (VI).
- **E6.4** Tracing distribuído — propagação de contexto OTel via REST e headers Kafka, trace
  contínuo order→payment→invoice, visualizado no SigNoz (VI).
- **E6.5** Alerting/SLOs básicos — usando o alerting nativo do SigNoz; sinais que a Fase 7 vai
  consumir (VI).
- **E6.6** Dashboards do SigNoz como código (export/import de JSON versionado no repo) (VI, IV).

### Fase 7 — SRE Autônomo
- **E7.1** Agente de detecção — escopo determinístico (crash loop, taxa de erro, violação de
  SLO), evitando anomaly-detection genérico (VII, VI).
- **E7.2** Agente de remediação/PR — diagnóstico + hotfix/PR auditável, linkando o alerta que
  disparou (VII).
- **E7.3** Trava de governança tecnicamente enforced — branch protection, required reviewers,
  nenhum auto-merge/auto-deploy possível mesmo tecnicamente. Implementa a garantia do
  Princípio VII, não só documenta (VII).
- **E7.4** Guardrails de escopo do agente — quais paths/tipos de mudança ele pode propor
  (VII).
- **E7.5** Auditoria das ações autônomas — trilha de quem/o quê/quando, usando a stack da
  Fase 6 (VII, VI).

## Ordem de execução recomendada

1. **Fase 1 completa (1.1→1.12) antes de tocar em Kafka.** Publicar eventos sobre uma
   máquina de estados sem guarda ou sem validação propagaria o problema pro resto do sistema
   (Princípio II); o build precisa estar confirmado saudável (1.1) antes de qualquer coisa
   ser empilhada em cima.
2. **Fase 2 (Kafka)** em seguida — sem produtor/consumidor real não há nada para orquestrar
   (Fase 3) nem tracing de mensageria para medir (Fase 6). Comece pelo contrato de evento
   (E2.1).
3. **Fase 4, hardening leve (E4.1–E4.3)**, pode ser intercalada logo após Fase 1/2 — já está
   "em andamento" e não depende de mais nada. Deixe E4.4 (Helm/Kustomize) para depois da
   Fase 2, evitando redesenhar os manifests duas vezes.
4. **Fase 3 (Airflow)** depois da Fase 2 — o caso de uso mais natural (reconciliação/timeout)
   precisa de eventos/estado reais para orquestrar.
5. **Fase 5 (Keycloak)** depois de 2+3+4 estabilizarem — colocar auth em todos os endpoints
   multiplicaria retrabalho de testes de integração enquanto a topologia ainda muda. Também é
   a ordem que a constitution já declara.
6. **Fase 6 (Observabilidade)** só depois de 1–5 — não dá para ter tracing distribuído sem
   chamadas REST+Kafka reais, sem DAGs reais, sem topologia de deploy estável, e idealmente
   com identidade já presente nos traces. Não contradiz o Princípio VI: os itens 1.12/E2.x/
   E3.4 já plantam logging mínimo cedo — o que fica para a Fase 6 é a stack completa e os
   dashboards.
7. **Fase 7 (SRE autônomo)** por último — depende inteiramente da telemetria da Fase 6.
