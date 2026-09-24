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

## Fase 1 (imediata) — endurecer o que existe

Persistência real e validação deixam de ser adiadas — ver Princípio III da constitution
(v3.0.0). Ordem sugerida = ordem de dependência técnica dentro da própria fase.

| # | Item | Descrição | Princípio | Arquivos/serviços |
|---|------|-----------|-----------|--------------------|
| 1.1 | Build health check | Rodar `mvnw clean verify` nos 3 serviços; confirmar se `spring-boot-starter-webmvc`, `spring-boot-starter-kafka` e as variantes `-test` resolvem de verdade no BOM do Spring Boot 4.1.1; se não forem canônicas, trocar por `spring-boot-starter-web`, `spring-kafka` e um único `spring-boot-starter-test`. | I | `*/pom.xml` |
| 1.2 | Remover toolchain Kotlin/Lombok morto | Remover `kotlin-stdlib`, `kotlin-maven-plugin`, `kotlin-test`, plugin Lombok de `payment-api`/`invoice-api` (zero uso real), ou decidir formalmente usar Kotlin em algum serviço como decisão explícita, não resíduo. | I, II | `payment-api/pom.xml`, `invoice-api/pom.xml` |
| 1.3 | Tratamento de erro HTTP — 404 real | `@RestControllerAdvice` (ou `@ResponseStatus`) mapeando `*NotFoundException` → 404, com corpo de erro consistente. Implementar 3x, um por serviço (sem lib compartilhada, Princípio I). | II | `*/exception/*NotFoundException.java`, novo `*/exception/GlobalExceptionHandler.java` |
| 1.4 | Validação de request | `spring-boot-starter-validation`; `@NotBlank/@NotNull/@Positive` em `OrderRequest`/`PaymentReservationRequest`/`InvoiceRequest`; `@Valid` nos controllers; handler de `MethodArgumentNotValidException` → 400. Decidir formato de `orderId`/`paymentId` (hoje `String` livre, sem validar UUID). | II | `*/dto/*Request.java`, controllers, `*/pom.xml` |
| 1.5 | Máquina de estados / guardas de transição | Bloquear transições ilegais (confirmar já cancelado etc.) com 409 Conflict; decidir o destino dos enum values mortos (`OrderStatus.COMPLETED`, `PaymentStatus.FAILED`, `InvoiceStatus.FAILED`) — implementar o gatilho real ou remover até terem uso. | II | `*/model/*Status.java`, `*/service/*Service.java` |
| 1.6 | Testes unitários de Service | Criação, busca, 404, transições válidas/bloqueadas, validação — Mockito/JUnit, sem contexto Spring. | II | `*/src/test/java/.../service/*ServiceTest.java` |
| 1.7 | Testes de Controller (`@WebMvcTest`) | Contrato HTTP: status codes (200/201/400/404/409), shape do JSON de erro. | II | `*/src/test/java/.../controller/*ControllerTest.java` |
| 1.8 | Teste de integração HTTP baseline | `@SpringBootTest` real substituindo o `contextLoads()` vazio: fluxo criar→confirmar/cancelar→buscar via `TestRestTemplate`/`WebTestClient`. | II | `*/src/test/java/.../*ApplicationTests.java` |
| 1.9 | Decisão de ownership de schema/DB | Definir, antes de ligar JPA: schema dedicado por serviço dentro de `apisdb`, ou bancos/instances separados — evita furar Princípio I por baixo do capô. | I, III | `infra/docker-compose.yml`, `infra/k8s/postgres-api-*.yaml` |
| 1.10 | Persistência JPA simétrica | Adicionar `spring-data-jpa`+`postgresql` aos 3 poms; `@Entity`+`Repository` substituindo os `ConcurrentHashMap`; Flyway/Liquibase (recomendado) vs `ddl-auto`; ativar `spring.datasource.*`/`spring.jpa.*` nos 3 `application.properties`. | II, III | `*/pom.xml`, `*/model/*.java`, novo `*/repository/*Repository.java`, `*/application.properties` |
| 1.11 | Testes de integração com Testcontainers | Após 1.10: `@SpringBootTest` + Testcontainers contra Postgres efêmero real. | II | `*/src/test/java/.../*IntegrationTest.java` |
| 1.12 | Instrumentação básica (logging estruturado) | Logs consistentes nos pontos de transição de estado — gancho mínimo do Princípio VI, não a stack completa da Fase 6. Prioridade menor, não bloqueia o resto. | VI | `*/service/*Service.java`, `*/src/main/resources/logback-spring.xml` (novo, opcional) |

**Sequenciamento interno**: 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6/1.7/1.8 (trava de regressão
antes de mexer em storage) → 1.9 → 1.10 → 1.11 → 1.12.

## Fases 2–7 — epics de alto nível

### Fase 2 — Kafka assíncrono
- **E2.1** Convenção de evento/tópico — nomeação, schema de payload, correlação (`orderId`
  em todo evento downstream). Decisão documentada, não lib compartilhada (I, II).
- **E2.2** `order-api` produtor — publicar `OrderCreated`/`OrderConfirmed`/`OrderCancelled`
  após cada transição persistida; considerar outbox pattern para não violar Princípio II (II).
- **E2.3** `payment-api` consumidor+produtor — `@KafkaListener` em `OrderCreated`, publica
  `PaymentReserved`/`PaymentFailed` (II).
- **E2.4** `invoice-api` consumidor+produtor — `@KafkaListener` em `PaymentReserved`, publica
  `InvoiceIssued`/`InvoiceFailed` (II).
- **E2.5** Estratégia de erro de consumo — retry + dead-letter topic por serviço (II).
- **E2.6** Testes de mensageria — Testcontainers Kafka por serviço (II).

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

### Fase 6 — Observabilidade (3 pilares)
- **E6.1** Seleção de stack — Prometheus+Grafana, Loki/ELK, Tempo/Jaeger, OpenTelemetry
  Collector como camada neutra (VI, IV).
- **E6.2** Métricas — Micrometer + `/actuator/prometheus` nos 3 serviços (VI).
- **E6.3** Logs estruturados centralizados — JSON com `trace_id`/`span_id`/id de entidade
  (VI).
- **E6.4** Tracing distribuído — OpenTelemetry SDK, trace contínuo order→payment→invoice
  (VI).
- **E6.5** Alerting/SLOs básicos — sinais que a Fase 7 vai consumir (VI).
- **E6.6** Dashboards como código, versionados (VI, IV).

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
