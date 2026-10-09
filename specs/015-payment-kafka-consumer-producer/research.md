# Research: payment-api como Consumidor e Produtor Kafka

Verificações feitas em 2026-10-04 contra o classpath real do `payment-api` (Spring Boot 4.1.1,
`spring-kafka` 4.1.1, `kafka-clients` 4.2.1, `jackson-databind` 3.1.5), via `jshell` e `javap`.
O lado **produtor** (outbox, relay, serializador, tópicos, timeouts) já foi decidido e validado
empiricamente no E2.2 — ver [`specs/014-order-kafka-producer/research.md`](../014-order-kafka-producer/research.md)
(Decisões 1–8). Aqui só se repete o que muda para o `payment-api` e se decide o lado
**consumidor**, que o E2.2 não cobriu. Pendências que dependem de Docker estão na Decisão 10.

## Decisão 1: Lado produtor — mesmo desenho do E2.2, copiado para o payment-api

**Decision**: o `payment-api` ganha o seu próprio outbox transacional (`outbox_events` no
`payment_db`) e o seu próprio relay `@Scheduled`, com o mesmo desenho do E2.2: o escritor exige
transação ativa (`MANDATORY`), serializa o evento (Jackson 3) no enqueue, e o relay lê o lote
mais antigo com `FOR UPDATE` bloqueante (sem `SKIP LOCKED`), publica um a um esperando o ack,
remove a linha confirmada e encerra o lote na primeira falha. Produtor `acks=all`, timeouts
curtos, `StringSerializer`, chave = `orderId`, sem header `__TypeId__`.

**Rationale**: os requisitos FR-006/007/008 são os mesmos do E2.2 e a solução foi validada
contra Kafka/Postgres reais. O `k8s` define `replicas: 3` também para o `payment-api`
(`infra/k8s/payment-service-deployment.yaml`), então o argumento do `FOR UPDATE` bloqueante
(relays de réplicas serializados, preservando a ordem por pedido) vale igual.

**Princípio I**: as classes são **copiadas** para dentro do `payment-api` (pacote próprio
`outbox/`), nunca compartilhadas — sem `pom.xml` raiz nem biblioteca comum (FR-013). A duplicação
é o preço consciente da independência; extrair uma lib compartilhada violaria a constitution.

**Alternatives considered**: lib comum entre serviços — rejeitada (Princípio I). Publicar direto
do listener — rejeitada: dual-write, perde evento se cair entre persistir e publicar.

## Decisão 2: Consumo — `@KafkaListener` com valor `String` e parse manual (Jackson 3)

**Decision**: um `@KafkaListener` em `order.created`, grupo `payment-api`, com
`StringDeserializer` para chave e valor, e `auto.offset.reset=earliest`. O listener faz o parse
do JSON com o `JsonMapper` (Jackson 3) autoconfigurado para uma classe de entrada declarada no
próprio `payment-api` (`OrderCreatedMessage`, só com os campos que o `payment-api` usa:
`eventId`, `orderId`, `amount`, mais `eventType`/`eventVersion`/`occurredAt`/`customerId` quando
úteis), tolerante a campos desconhecidos.

**Rationale**: espelha a escolha do E2.2 (texto + Jackson 3, sem depender de header de tipo).
O `JacksonJsonDeserializer` exigiria `trusted packages` e o header `__TypeId__`, que o
`order-api` deliberadamente **não** emite; com ele, o consumidor falharia ou acoplaria os
serviços (Princípio I). Parse manual no listener também deixa a política de erro explícita
(Decisão 4): JSON ilegível vira erro **permanente** tratado de um jeito, falha de banco vira erro
**transitório** tratado de outro.

**Verificado**: o `JsonMapper.builder().build()` do Jackson 3 já vem com
`FAIL_ON_UNKNOWN_PROPERTIES=false`; `com.fasterxml.jackson.annotation.JsonIgnoreProperties` existe
no classpath e será usada para explicitar a tolerância (o contrato do E2.2 diz que consumidores
devem ignorar campos desconhecidos). `auto.offset.reset` e `enable.auto.commit` não têm padrão no
Boot (nulos) → o padrão do cliente Kafka (`latest`) valeria; por isso `earliest` é configurado
explicitamente, atendendo à "Pedidos anteriores à feature" da spec.

**Alternatives considered**: `JacksonJsonDeserializer` com `spring.json.value.default.type` e
`use.type.headers=false` — funciona, mas a falha de desserialização vira exceção do container
(`ErrorHandlingDeserializer` seria necessário), espalhando a política de erro em três lugares.

## Decisão 3: Idempotência e "um pagamento por pedido" — constraints no próprio `payments`

**Decision**: a tabela `payments` ganha a coluna nula `source_event_id UUID` e dois índices
únicos: um em `source_event_id` (dedup por `eventId`, FR-004) e um **parcial** em `order_id
WHERE source_event_id IS NOT NULL` (no máximo um pagamento decidido por evento por pedido,
FR-005). O processamento de um `OrderCreated` é **uma única transação**: checa duplicata
(`existsBySourceEventId` e existência de pagamento originado de evento para o `orderId`), insere
o pagamento com a situação decidida e grava o evento de saída no outbox. O pagamento **é** o
marcador de "evento já tratado"; não existe tabela separada de eventos processados.

**Rationale**: a atomicidade exigida pelo FR-006 (decisão + evento pendente + marca de
processado) sai de graça numa só transação, sem tabela extra. O índice parcial deixa a API REST
intacta: pagamentos criados por `POST /api/payments` (`source_event_id` nulo) podem continuar
repetindo `orderId` como hoje (FR-012). As constraints são a **rede de segurança** contra a
corrida de dois consumidores (rebalanceamento entre as 3 réplicas): quem perde viola a
constraint, a transação reverte, o registro é reentregue e na segunda vez a pré-checagem o
descarta como duplicata — convergência garantida sem lock explícito.

**Alternatives considered**: tabela `processed_events(event_id)` — rejeitada: uma tabela e um
insert a mais por mensagem sem benefício, e ainda precisaria de uma regra separada para "um por
pedido". `UNIQUE(order_id)` global em `payments` — rejeitada: quebraria o contrato REST atual.
Confiar só em offset commit — rejeitado: entrega é pelo menos uma vez.

## Decisão 4: Política de erro do consumo — nunca perder transitório, não travar em venenoso

> **Substituída pelo E2.5** ([spec 017](../017-consumer-retry-dlt/spec.md)): o retry sem limite e o log+descarte aqui
> descritos eram a política interina; hoje o consumo usa retry limitado + DLT `<tópico>.dlt`.

**Decision**: registrar um bean `CommonErrorHandler` (`DefaultErrorHandler`) que o Boot aplica
sozinho ao container. Dois tratamentos:
- **Transitório** (banco indisponível, erro ao gravar): retry **ilimitado** com espera fixa de 1 s
  (`FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS)`) — o registro **não é descartado**; o
  consumo daquela partição espera o banco voltar.
- **Permanente** (JSON ilegível, campo obrigatório ausente): o listener captura a falha de parse/
  validação, loga em `ERROR` com topic/partition/offset e um trecho truncado do valor, e
  **retorna normalmente** (offset avança) — não trava as mensagens seguintes (FR-011).

**Rationale (evidência empírica)**: o padrão do `DefaultErrorHandler` é
`SeekUtils.DEFAULT_BACK_OFF = FixedBackOff(0, 9)` — 10 tentativas **sem espera** e depois o
registro é **descartado** (logado). Com um banco fora por mais de alguns milissegundos isso
perderia silenciosamente `OrderCreated`s, violando FR-006/FR-008. O custo do retry ilimitado é o
bloqueio na cabeça da partição durante a falha — exatamente o trade-off já aceito no relay do
E2.2 (a falha fica visível por log a cada tentativa). Retry com limite, dead-letter e
estacionamento de mensagem venenosa são o **E2.5** (FR-016).

**Verificado**: o Boot injeta um bean `CommonErrorHandler` no `KafkaAnnotationDrivenConfiguration`
(parâmetro `ObjectProvider<CommonErrorHandler>`, confirmado por `javap`); `FixedBackOff` e
`UNLIMITED_ATTEMPTS` (`Long.MAX_VALUE`) existem no `spring-core` 7.0.9.

**Alternatives considered**: manter o padrão — rejeitado (perde dados). Retry ilimitado também
para venenoso — rejeitado: uma mensagem ruim travaria a partição para sempre. `DeadLetterPublishing
Recoverer` — é o E2.5.

## Decisão 5: Regra de decisão do pagamento — limite de valor configurável

**Decision**: `PaymentDecisionPolicy` decide por valor: `amount > limit` → `FAILED` com motivo
`AMOUNT_LIMIT_EXCEEDED`; caso contrário → `RESERVED`. O limite é a property
`payment.approval.limit` (padrão `1000.00`, `BigDecimal`). Função pura, sem relógio nem
aleatoriedade (FR-002).

**Rationale**: simples, determinística e testável nos dois caminhos (Princípio II), com um valor
que o `order-api` já carrega no `OrderCreated`. Comparação por `compareTo` (escala-insensível):
`1000` e `1000.00` são iguais → exatamente no limite é "dentro" (edge case da spec).

## Decisão 6: Nova situação `FAILED` no `Payment` e evolução do esquema

**Decision**: `PaymentStatus` ganha `FAILED`. `Payment` ganha o campo `sourceEventId` e um
construtor/fábrica para pagamentos originados de evento (situação inicial `RESERVED` ou `FAILED`
conforme a decisão). Migração Flyway `V2__payment_event_support.sql`: `ALTER TABLE payments ADD
COLUMN source_event_id UUID`, índice único em `source_event_id`, índice único parcial em
`order_id WHERE source_event_id IS NOT NULL`, e `CREATE TABLE outbox_events` (mesmo esquema do
E2.2, ver [data-model.md](./data-model.md)). `PaymentResponse` e as rotas não mudam; só pode
aparecer o valor `"FAILED"` em consultas (FR-012). `confirm`/`cancel` de um pagamento `FAILED`
caem no `InvalidStatusTransitionException` já existente (409), sem código novo.

**Rationale**: o motivo da falha viaja no evento e nos logs; não há necessidade de coluna de
motivo para o laboratório (YAGNI). `status VARCHAR(20)` comporta `FAILED`.

## Decisão 7: Tópicos e configuração do consumidor/produtor

**Decision**:
- `NewTopic` para `payment.reserved` e `payment.failed` (3 partições, 1 réplica) e **também para
  `order.created`** (3/1, idêntico ao declarado pelo `order-api`): `KafkaAdmin` só cria o que
  falta, então é seguro e evita a corrida de boot.
- Consumidor com `allow.auto.create.topics=false`.
- Container de listener com concorrência 1 (padrão) — uma thread consome todas as partições
  atribuídas; a ordem por pedido é preservada trivialmente. `ack-mode` padrão (`BATCH`): o offset
  só é confirmado depois que o método do listener retorna, isto é, **depois** do commit da
  transação do processamento (at-least-once, nunca at-most-once).

**Rationale**: sem `NewTopic` para `order.created`, um `payment-api` que subisse antes do
`order-api` poderia causar a criação automática do tópico pelo broker com 1 partição (consumidor
com `allow.auto.create.topics=true` é o padrão do cliente), mudando a topologia que o E2.2
validou. Duplicar a declaração idêntica é inofensivo e mantém cada serviço autossuficiente
(Princípio I e IV).

**Verificado**: `missingTopicsFatal=false` por padrão no Boot (o serviço sobe mesmo sem o
tópico). **A validar**: o tópico `order.created` mantém 3 partições após ambos os serviços subirem
em ordens diferentes.

## Decisão 8: Observabilidade — logs e `trace_id` de origem, sem propagar por header

**Decision**: (a) ligar `spring.kafka.listener.observation-enabled=true` **só no listener**, para
que o processamento de cada mensagem rode dentro de um span e os logs do consumo saiam com
`traceId`/`spanId` (FR-009); (b) log INFO de recebimento/decisão/duplicata/descarte, com
`orderId`, `eventId`, `eventType`, `topic`, `partition`, `offset`; (c) o `traceId` do MDC é
gravado em `outbox_events.trace_id` no enqueue e reaparece nos logs do relay como
`originTraceId` (mesmo padrão do E2.2); (d) **não** ligar a observação do `KafkaTemplate`
(`spring.kafka.template.observation-enabled`, padrão `false`) — nenhum contexto de trace é
escrito em header de saída; leitura/escrita de contexto entre serviços é o E6.4 (FR-016).

**Rationale**: sem a observação no listener, a thread do consumidor não tem trace ativo e os
logs sairiam sem `traceId`, falhando o FR-009. O span é local ao `payment-api`; sem header de
entrada com contexto (o `order-api` não envia), cada mensagem começa um trace novo. **Verificado**:
a property `spring.kafka.listener.observation-enabled` existe (`KafkaProperties$Listener`,
padrão `false`). **A validar**: logs do listener realmente trazem `traceId` com a observação
ligada.

## Decisão 9: Estratégia de testes — sem dependência nova, sem Testcontainers Kafka

**Decision**: nenhum artifact novo no `pom.xml`. Testes:
- Unidade: `PaymentDecisionPolicyTest` (limite, igual ao limite, determinismo), eventos
  (formato vs contrato do E2.1), escritor e relay (`KafkaTemplate` mockado: ordem, remoção
  após ack, parada na primeira falha), listener (JSON inválido é descartado com log, falha
  transitória propaga).
- Integração (`PaymentApiApplicationTests`, Postgres via Testcontainers como no 1.11,
  `@MockitoBean KafkaTemplate`, relay com intervalo alto): o serviço de processamento é chamado
  diretamente com `OrderCreated`s — cobre FR-002 (reservado/falhou), FR-004 (reentrega não
  duplica), FR-005 (dois `eventId` para o mesmo pedido), FR-006 (decisão + linha de outbox na
  mesma transação, com falha forçada revertendo ambas) e que `POST /api/payments` continua igual
  (FR-012).
- Kafka real: validação empírica manual no [quickstart](./quickstart.md), como no E2.2. A suíte
  Testcontainers Kafka é o E2.6 (FR-016).

`./mvnw test` continua autossuficiente (FR-015): só exige runtime Docker.

## Decisão 10: Pendências que dependem de Docker (validar durante a implementação)

1. Migração `V2` aplica sobre um `payment_db` com dados de `V1` e `ddl-auto=validate` aceita
   `source_event_id` (UUID) e `outbox_events.payload TEXT` (mitigação:
   `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`).
2. Injeção de `KafkaTemplate<String, String>` a partir do bean `KafkaTemplate<?, ?>` (já
   confirmada no `order-api`; reconfirmar aqui).
3. Violação das constraints sob concorrência reverte a transação inteira e a reentrega converge
   (dois processamentos simultâneos do mesmo `eventId`).
4. Tópico `order.created` com 3 partições independentemente da ordem de subida dos serviços;
   `payment.reserved`/`payment.failed` com 3 partições, chave `orderId`, sem header `__TypeId__`.
5. Fluxo ponta a ponta com Kafka real: `order-api` cria pedido → `payment-api` decide →
   `payment.reserved`/`payment.failed`; broker parado/religado; `kill -9` entre persistir e
   publicar; reentrega forçada do mesmo `OrderCreated`; banco do `payment-api` indisponível e
   religado sem perda (Decisão 4).
6. Logs do consumo trazem `traceId` com `observation-enabled=true` e o relay traz
   `originTraceId`.

## Riscos e limitações conhecidas

- **Bloqueio na cabeça da partição**: com retry ilimitado para falha transitória, um erro que na
  verdade é permanente (ex.: bug de mapeamento que sempre lança `RuntimeException` não
  classificada) trava a partição. Fica visível em log a cada tentativa; estacionamento/DLT é o
  E2.5. Para reduzir o risco, só falhas de parse/validação são tratadas como permanentes e
  descartadas; todo o resto repete.
- **Duplicação consciente do código de outbox/relay** entre `order-api` e `payment-api`
  (Princípio I). O `invoice-api` (E2.4) receberá uma terceira cópia.
- **Ordem só por pedido**, não global; e `OrderCreated`/`PaymentReserved` estão em tópicos
  diferentes — a ordem entre tópicos é a de publicação do relay (herdado do E2.2).
- **Eventos de saída de pagamentos REST**: `POST /api/payments` não publica eventos (FR-012);
  o evento só existe para pagamentos decididos a partir de `OrderCreated`.

## Validações empíricas (implementação, 2026-10-04)

Docker ligado; Postgres 15 e Kafka `apache/kafka:4.2.0` reais; `order-api` (8081) e
`payment-api` (8082) rodando com `spring-boot:run`. Linha de base antes da feature:
`./mvnw clean verify` com `BUILD SUCCESS`. Pendências da Decisão 10:

1. **Migração `V2` e `ddl-auto=validate`** — aplicou sobre um `payment_db` já existente com
   dados do `V1`; `source_event_id` (UUID) e `payload TEXT` validaram sem mitigação. ✅
2. **`KafkaTemplate<String, String>`** — injeção direta funciona (serviço subiu e publicou). ✅
3. **Corrida no mesmo `eventId`** — teste de integração com duas threads: no máximo uma
   transação vence; reprocessar em seguida converge para 1 pagamento e 1 linha de outbox. ✅
4. **Tópicos** — `order.created`, `payment.reserved` e `payment.failed` com `PartitionCount: 3`,
   `ReplicationFactor: 1`; mensagens com chave = `orderId` e `NO_HEADERS` (sem `__TypeId__`). ✅
5. **Ponta a ponta com Kafka real**:
   - Ao subir, o `payment-api` leu o backlog de `order.created` (12 eventos da validação do E2.2) e
     criou 12 pagamentos `RESERVED` — confirma "pedidos anteriores à feature" (`earliest`).
   - `amount` 10.50 → `PaymentReserved`; 1500 → `PaymentFailed` (`AMOUNT_LIMIT_EXCEEDED`);
     exatamente 1000 → `PaymentReserved`. Outbox do `payment-api` esvazia em segundos.
   - **Reentrega**: o mesmo `OrderCreated` (mesmo `eventId`) enviado 2× e um terceiro com
     `eventId` novo e o mesmo `orderId` → 3 logs "Duplicate OrderCreated ignored", continua 1
     pagamento e 1 evento em `payment.reserved` para o pedido.
   - **Ilegíveis**: `lixo-nao-json`, `{"eventId":"x",...}` e `{"amount":5}` → 3 logs `ERROR`
     com topic/partition/offset/trecho; o `OrderCreated` válido seguinte foi processado e o
     serviço ficou `UP`.
   - **Broker parado**: 3 pedidos criados com o Kafka fora; o outbox do `order-api` acumulou 3;
     o `payment-api` ficou `UP`. Ao religar, os dois outboxes esvaziaram (~27 s, incluindo o
     reingresso do consumidor no grupo) e os 3 resultados chegaram (2 reservados, 1 falhou).
   - **`kill -9` entre persistir e publicar**: com o relay desligado por config
     (`OUTBOX_RELAY_INTERVAL_MS=3600000`) a decisão ficou persistida e o evento no outbox, **0**
     no tópico; após `kill -9` e restart normal, o evento foi publicado (1 no tópico, 1 pagamento).
   - **Postgres parado** com 3 `OrderCreated` injetados: o `payment-api` seguiu vivo; ao religar o
     banco, as 3 mensagens viraram pagamentos (RESERVED, FAILED, RESERVED) e o outbox esvaziou —
     **nenhuma mensagem descartada**. Observação: durante a queda o Hikari bloqueia ~30 s por
     tentativa esperando conexão, então vimos uma tentativa de consumo e não o ciclo de 1 s do
     error handler; o resultado (sem perda) é o mesmo. Uma queda de banco acima de
     `max.poll.interval.ms` (5 min) faria o consumidor sair do grupo e as mensagens serem
     reentregues — continua at-least-once.
6. **Observabilidade** — `OrderCreated received`, `Event enqueued in outbox` e `Payment decided`
   saem com o **mesmo** `traceId` (thread do listener, `observation-enabled=true`); o log do
   relay (`Event published to Kafka`) traz o seu `traceId` e o do consumo em `originTraceId`. ✅

Outras verificações: Compose e k8s já definem `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` para o
`payment-api` (k8s com `replicas: 3`); nenhuma alteração de infraestrutura foi necessária
(Princípio IV). `./mvnw clean verify`: 52 testes, `BUILD SUCCESS`; `pmd:pmd`: 0 violações;
`order-api`, `invoice-api` e `pom.xml` do `payment-api` sem alteração.

Achados de implementação: (a) `@MockitoSpyBean` em bean `@Transactional(MANDATORY)` fica atrás do
proxy — stubar pelo alvo (`AopTestUtils.getUltimateTargetObject`), não pelo proxy; (b) a política de
retry do consumo foi extraída para `KafkaConsumerConfig.transientFailureBackOff()` para ser testável
sem reflexão.
