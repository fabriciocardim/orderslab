# Research: Testes de Mensageria com Kafka Real (Testcontainers)

Verificações de 2026-10-04 contra o classpath real (Spring Boot 4.1.1, Testcontainers 2.0.5,
`spring-kafka` 4.1.1, `kafka-clients` 4.2.1). Esta feature é só infraestrutura de teste: nada de
`src/main` muda (FR-013). O que cada teste valida vem das specs
[014](../014-order-kafka-producer/spec.md), [015](../015-payment-kafka-consumer-producer/spec.md),
[016](../016-invoice-kafka-consumer-producer/spec.md) e [017](../017-consumer-retry-dlt/spec.md); as seções
"Validações empíricas" delas são o roteiro do que automatizar. Pendências que dependem de Docker: Decisão 8.

## Decisão 1: Dependência de teste `testcontainers-kafka` (única mudança de pom)

**Decision**: acrescentar em cada `pom.xml` (`order-api`, `payment-api`, `invoice-api`)
`org.testcontainers:testcontainers-kafka` com `<scope>test</scope>` e **sem versão** (o BOM do Boot
gerencia).

**Rationale (evidência)**: `help:effective-pom` mostra `testcontainers-kafka` **2.0.5** gerenciado, mesma
versão de `testcontainers-postgresql` já usado desde o item 1.11. Nenhuma outra dependência é necessária:
`spring-boot-starter-kafka-test` (spring-kafka-test) e `kafka-clients` já estão no classpath de teste.

## Decisão 2: `@ServiceConnection` com `org.testcontainers.kafka.KafkaContainer` (imagem `apache/kafka`)

**Decision**: em cada classe de teste, `@Container @ServiceConnection static KafkaContainer kafka = new
KafkaContainer("apache/kafka:4.2.0")` ao lado do `PostgreSQLContainer` existente (também `@ServiceConnection`).
O Boot injeta sozinho `spring.kafka.bootstrap-servers` e o datasource.

**Rationale (evidência)**: `spring-boot-kafka-4.1.1.jar` traz
`org.springframework.boot.kafka.testcontainers.ApacheKafkaContainerConnectionDetailsFactory` (também há
Confluent e Redpanda) — o `@ServiceConnection` do `KafkaContainer` Apache funciona sem `@DynamicPropertySource`.
A imagem `apache/kafka:4.2.0` é a mesma do `infra/docker-compose.yml`, então os testes refletem o broker real do
laboratório (modo KRaft de nó único).

**Alternatives considered**: `ConfluentKafkaContainer` — rejeitado: outra imagem, comportamento diferente do
broker usado no laboratório. `EmbeddedKafka` do spring-kafka-test — rejeitado: não é o broker real, e a spec pede
Testcontainers.

## Decisão 3: Um broker e um banco por classe de teste, isolamento por identificadores únicos

**Decision**: cada serviço tem **uma** classe de mensageria (`OrderMessagingTest`, `PaymentMessagingTest`,
`InvoiceMessagingTest`) com contêineres `static` (sobem uma vez por classe) e testes isolados por `UUID`
próprios: cada teste gera seus `orderId`/`eventId`, publica/consome e **filtra pela chave**. Sem limpeza entre
testes, sem ordem dependente (FR-008).

**Rationale**: subir um Kafka por teste custaria ~10 s cada. Como a chave é o `orderId` e cada teste cria os seus,
as mensagens de outros testes nunca interferem. O consumidor de teste sempre lê "desde o início" e filtra.

## Decisão 4: Consumidor de teste = `KafkaConsumer` de `kafka-clients` com `assign` + `seekToBeginning`

**Decision**: classe auxiliar de teste `KafkaTestSupport` (um **arquivo por serviço**, copiado — nada
compartilhado, FR-012) com: `consumerFor(topic)` (propriedades `bootstrap.servers` do contêiner, `group.id`
aleatório, `StringDeserializer`, `assign` de todas as partições e `seekToBeginning`); `awaitRecords(topic, keyFilter,
expectedCount, timeout)` que faz **polling em laço com prazo** (`Duration`), acumula só os registros cuja chave
casa e, no estouro, falha com mensagem descritiva ("esperava N registro(s) com chave X em T; chegaram M");
`assertNoMore(topic, key, quietPeriod)` para provar não-duplicação; e `headerNames(record)`.

**Rationale**: `assign` evita a espera de rebalanceamento do `subscribe`; `seekToBeginning` torna cada leitura
independente do offset; polling com prazo atende o FR-009 (nenhuma pausa fixa como sincronização). O período de
silêncio de `assertNoMore` é a única espera "fixa", e é uma **asserção negativa** (não há evento para aguardar),
mantida curta (≈ 3× o intervalo do relay).

## Decisão 5: Parâmetros de teste para rapidez e estabilidade (só propriedades de teste)

**Decision**: por `@SpringBootTest(properties = {...})`:
- `outbox.relay.interval-ms=200` (publicação quase imediata);
- `payment-api`/`invoice-api`: `consumer.retry.initial-interval-ms=3000` e demais valores padrão — **de
  propósito lento** para provar que mensagem inválida vai ao DLT **sem retry** (se houvesse retry, o caminho
  levaria ≥ 3+6+10+10 s; a asserção exige chegada ao DLT em poucos segundos);
- nenhuma mudança em `src/main`.

**Rationale**: tornar o retry lento no teste é a forma mais simples e determinística de distinguir "direto ao DLT"
de "tentou antes" sem inspecionar logs. A falha transitória por indisponibilidade de banco **não** é automatizada
aqui (exigiria pausar o Postgres do mesmo contexto — frágil); ela segue coberta por testes de unidade do handler
(item E2.5) e pela validação manual registrada.

## Decisão 6: Broker indisponível — `pauseContainerCmd` / `unpauseContainerCmd`

**Decision**: no `order-api`, o teste de indisponibilidade pausa o contêiner com
`kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()`, cria/confirma pedidos pela API (espera
201/200), confirma por JDBC que as linhas continuam em `outbox_events`, e **retoma** com `unpauseContainerCmd` num
`finally` (FR-003: sempre retomar, mesmo se o teste falhar). Depois aguarda (polling com prazo de 60 s) os eventos
nos tópicos, exatamente um por operação.

**Rationale**: pausar congela o broker sem derrubar a porta mapeada, então o endereço anunciado continua válido ao
retomar (diferente de `stop`/`start`, que muda a porta mapeada do Testcontainers). O produtor já tem timeouts curtos
(E2.2: `max.block.ms=5000`, `delivery.timeout.ms=10000`), e o relay tenta a cada ciclo. **A validar** que a
recuperação após `unpause` é rápida e estável.

## Decisão 7: Cobertura por serviço (matriz teste ↔ requisito)

| Serviço | Teste | Requisito |
|---|---|---|
| order-api | criar/confirmar/cancelar → 1 evento por operação, tópico, chave, sem `__TypeId__`, envelope | FR-002, FR-007 |
| order-api | tópicos com 3 partições (`AdminClient`) | FR-002 |
| order-api | ordem criação < confirmação/cancelamento por pedido (timestamps do produtor) | FR-002 |
| order-api | broker pausado: HTTP normal, outbox retém, eventos entregues ao retomar, sem duplicata | FR-003 |
| payment-api | `OrderCreated` baixo → `PaymentReserved`; alto → `PaymentFailed`; chave = `orderId` | FR-004 |
| payment-api | mesmo `eventId` publicado 3× → 1 pagamento e 1 evento | FR-004 |
| payment-api | inválidas → `order.created.dlt` (chave/valor idênticos, headers `kafka_dlt-*`, sem retry); válida seguinte processada | FR-006 |
| payment-api | `payment.reserved`/`payment.failed`/DLT com 3 partições; sem `__TypeId__` | FR-007 |
| invoice-api | espelho com `PaymentReserved` → `InvoiceIssued`/`InvoiceFailed` e `payment.reserved.dlt` | FR-005, FR-006, FR-007 |

## Decisão 8: Pendências que dependem de Docker (validar na implementação)

1. A imagem `apache/kafka:4.2.0` sobe como `org.testcontainers.kafka.KafkaContainer` e o `@ServiceConnection`
   injeta o bootstrap (o serviço publica/consome).
2. `pause`/`unpause` do broker recupera sem mudar o endereço e o relay entrega sem perda nem duplicata.
3. Tempo de `./mvnw verify` por serviço: acréscimo ≤ ~2 min (SC-004).
4. Estabilidade: a suíte de mensageria passa 10× seguidas por serviço (SC-003).
5. Mutação: quebrar de propósito (remover a chave `orderId`; trocar o tópico) faz um teste novo falhar com
   mensagem clara (SC-005).
6. `./mvnw verify` e `pmd:pmd` limpos; testes existentes intactos.

## Riscos e limitações conhecidas

- **Duração**: cada classe sobe um Postgres e um Kafka (~10–20 s); aceito (SC-004).
- **Contêiner `apache/kafka` precisa ser baixado** na primeira execução (como o Postgres já é).
- **Teste de indisponibilidade é o mais sensível a tempo**: usa prazos folgados (60 s) e polling; se mostrar
  intermitência, é o primeiro candidato a ajuste.
- **Duplicação consciente** dos auxiliares de teste entre os três serviços (Princípio I).

## Validações empíricas (implementação, 2026-10-04)

Docker ligado; os testes sobem os próprios Postgres (`postgres:15-alpine`) e Kafka (`apache/kafka:4.2.0`) via
Testcontainers 2.0.5 — os contêineres do Compose não são usados. A máquina de desenvolvimento tem **8 GB de RAM** e,
durante a validação, o swap chegou a 13,3 GB de 14,3 GB (ver "Achados"). Pendências da Decisão 8:

1. **Imagem e `@ServiceConnection`** — `apache/kafka:4.2.0` sobe como `org.testcontainers.kafka.KafkaContainer`
   (~28 s) e o `@ServiceConnection` injeta o bootstrap; o serviço publica e consome. ✅
2. **`pause`/`unpause` do broker (order-api)** — com o contêiner pausado, 3 `POST` (201) e 1 `confirm` (200)
   responderam normalmente, as 4 linhas ficaram no `outbox_events`; ao retomar, todos os eventos chegaram e o
   outbox esvaziou. Recuperação estável nas 10 execuções finais. ✅
3. **Tempo** — linha de base (`clean verify`) antes: order 142 s, payment 133 s, invoice 90 s; depois, com a
   suíte de mensageria: order 168–214 s, payment 138–158 s, invoice 123–193 s (variam com a carga do host) — dentro de
   ~2 min de acréscimo (SC-004). Testes novos: order +4 (46), payment +5 (62), invoice +5 (62). ✅
4. **Estabilidade (SC-003)** — com o código final dos testes, **10 execuções consecutivas verdes por serviço**
   (30 no total), em sequência: order-api 10/10, payment-api 10/10, invoice-api 10/10. ✅ (ver "Achados" sobre as
   tentativas anteriores que falharam e o que cada uma ensinou)
5. **Mutação (SC-005)** — (a) trocar a chave do `send` do relay do `order-api` por um valor fixo: 3 de 4 testes
   falharam com "esperava 1 registro(s) com chave … em order.created em PT30S; chegaram 0"; (b) publicar o
   `PaymentReserved` no tópico `payment.failed` no `payment-api`: 3 de 5 falharam com "esperava 1 registro(s) com chave
   … em payment.reserved …; chegaram 0". Ambas revertidas (`git status` sem mudanças em `src/main`). ✅
6. **`./mvnw clean verify` e `pmd:pmd`** — BUILD SUCCESS e 0 violações nos três serviços; nenhuma mudança em
   `src/main`; as suítes existentes seguem verdes. ✅

## Achados de implementação

- **Entrega pelo menos uma vez aparece nos testes.** Numa rodada com o broker engasgado (ack > `delivery.timeout`
  de 10 s), o relay reenviou e o `order.created` de um pedido ficou com **3 cópias idênticas** (mesmo `eventId`).
  É exatamente o contrato do E2.2 (cópias com `eventId` estável; o consumidor deduplica). Os testes passaram a
  contar **eventos distintos** (por valor) em vez de registros: cópias da mesma mensagem são reentrega aceita; dois
  eventos *diferentes* para a mesma chave continuam sendo falha.
- **Partida a frio do produtor de teste.** O `KafkaTemplate` de produção usa `max.block.ms=5000`; logo após o broker
  subir, o metadata pode passar disso e o `send` lança a `TimeoutException` **do Kafka** antes de enviar. O `publish`
  dos testes insiste até um prazo (nada foi enviado, então não duplica); `consumerFor` também tolera o timeout de
  metadata.
- **Prazos folgados e "DLT sem retry" provado por prazo.** Nos consumidores o retry de teste é de 60 s (teto 120 s):
  se uma mensagem inválida fosse repetida antes do DLT, levaria > 7 min e estouraria os 45 s de espera, que por sua
  vez toleram picos de latência do broker.
- **Máquina com pouca memória.** Com 8 GB e swap quase cheio, o Kafka de teste chegou a não subir em 60 s e acks
  passaram de 10 s. Mitigações só de teste (úteis também em CI pequeno): heap do Kafka limitado
  (`KAFKA_HEAP_OPTS=-Xms128m -Xmx384m`, contra 1 GB do padrão da imagem) e `withStartupTimeout(3 min)` nos contêineres.
- **Rodar os 3 serviços em paralelo sufoca o Docker** (3 pares de contêineres + 3 JVMs): a 1ª tentativa de
  estabilidade em paralelo falhou nos três por timeouts de infraestrutura; o protocolo correto é sequencial. Também:
  `pkill` no script de loop **não** mata o `mvn` filho — conferir `pgrep surefire` antes de recomeçar.
