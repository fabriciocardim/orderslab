# Research: order-api como Produtor Kafka

Verificações feitas em 2026-09-29 contra o classpath real do `order-api` (Spring Boot 4.1.1,
`spring-kafka` 4.1.1, `kafka-clients` 4.2.1, Hibernate 7.4.5, `jackson-databind` 3.1.5), via
`jshell` e inspeção de bytecode (`javap`). **O daemon Docker estava parado** na máquina durante
o planejamento, então tudo que exige Postgres/Kafka reais está marcado como **A validar** e
listado na Decisão 10 — não foi assumido como confirmado.

## Decisão 1: Outbox transacional com relay por polling

**Decision**: cada transição persistida grava, **na mesma transação** que altera o pedido, uma
linha numa tabela `outbox_events` do próprio `order_db`. Um componente agendado (relay) lê as
linhas pendentes, publica no Kafka e as remove. A requisição HTTP nunca fala com o Kafka.

**Rationale**: é a única opção testada que cobre os três requisitos duros da spec ao mesmo
tempo: FR-005 (sem divergência, mesmo com queda entre persistir e publicar), FR-006 (broker
fora do ar não afeta a requisição) e a ordem por pedido. Como nenhum I/O de rede acontece dentro
da transação da requisição, ela fica curta, o que também mantém o lock da Decisão 2 barato.

**Alternatives considered**:
- Publicar direto no `Service` depois do `save` — rejeitado: dual-write. Broker fora do ar faz
  a requisição falhar (ou perder o evento se o erro for engolido), e uma queda entre o commit e
  o `send` perde o evento para sempre (viola FR-005/FR-006).
- `@TransactionalEventListener(AFTER_COMMIT)` publicando no Kafka — rejeitado: melhora o caso
  de rollback, mas continua perdendo o evento se o processo cair entre o commit e o envio.
- Transação Kafka + transação JPA — rejeitado: não existe atomicidade entre os dois sistemas
  sem um coordenador XA, que o laboratório não tem.
- CDC (Debezium/Kafka Connect) lendo o WAL — rejeitado por ora: exige Kafka Connect e
  configuração de replicação lógica no Postgres, infraestrutura nova bem maior que o escopo do
  item (Princípio IV: mais uma peça para portar). Pode ser revisitado como evolução.

## Decisão 2: Fronteira transacional + lock pessimista de escrita no pedido

**Decision**: `create`/`confirm`/`cancel` do `OrderService` passam a ser `@Transactional`. Em
`confirm`/`cancel`, o pedido é lido com `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)` num
método novo do `OrderRepository`). O gravador de eventos exige transação ativa
(`Propagation.MANDATORY`), para que seja impossível enfileirar um evento fora dela.

**Rationale**: o código atual lê, altera e salva sem transação nem controle de concorrência.
Duas requisições simultâneas (`confirm` e `cancel`) podem ambas ler `PENDING` e ambas vencer
(FR-009). Com o lock, a segunda espera a primeira confirmar a transação, então lê o estado
final e cai no `InvalidStatusTransitionException` que já existe: **409, sem nenhum mapeamento
novo e sem mudar o contrato HTTP** (FR-011). Efeito colateral valioso: transições do mesmo
pedido ficam serializadas, então o `id` crescente do outbox reflete a ordem real das
transições daquele pedido (base do FR-008).

**Alternatives considered**: `@Version` (lock otimista) — rejeitado: exige coluna nova em
`orders`, um mapeamento novo de `ObjectOptimisticLockingFailureException` e, para não devolver
500, ou um retry ou um 409 inédito para o cliente (mudança de contrato observável).

**Verificado**: `LockModeType.PESSIMISTIC_WRITE` e `org.springframework.data.jpa.repository.Lock`
existem no classpath. **A validar** (precisa de Postgres): o SQL emitido pelo Hibernate 7
(`for no key update` ou `for update`) e o teste de concorrência real (1×200, 1×409).

## Decisão 3: Esquema `outbox_events` e limpeza por remoção após a entrega

**Decision**: tabela nova via migração Flyway `V2__create_outbox_events_table.sql`, com `id`
identity (ordem), `event_id` único, `event_type`, `topic`, `aggregate_id` (o `orderId`, usado
como chave Kafka), `payload` (JSON já serializado, `TEXT`), `trace_id` (de origem, opcional),
`occurred_at` e `created_at`. Após a confirmação do envio, a linha é **removida** na mesma
transação do relay. Detalhes em [data-model.md](./data-model.md).

**Rationale**: remover ao publicar resolve o edge case "registro não cresce indefinidamente"
sem job de limpeza separado, e mantém a tabela contendo só o que está pendente (o
`ORDER BY id` fica trivial). A trilha de auditoria fica no próprio tópico Kafka e nos logs
(FR-010). Se o processo cair entre o ack do broker e o commit da remoção, a linha continua
lá e é reenviada — uma duplicata com o **mesmo `eventId`**, exatamente o contrato de
entrega "pelo menos uma vez" do FR-007.

`payload` como `TEXT`, não `jsonb`: nunca consultamos dentro do JSON, ele já é o formato
de fio, e `jsonb` normalizaria espaços e ordem de chaves sem ganho.

**Alternatives considered**: manter a linha e marcar `published_at` + job de purge —
rejeitado: mais partes móveis (job, índice parcial, política de retenção) para uma trilha que
o Kafka e os logs já fornecem.

**A validar**: `ddl-auto=validate` com coluna `TEXT` mapeada para `String` (Hibernate compara
pelo tipo JDBC; esperado `VARCHAR`). Mitigação pronta se falhar: `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`.

## Decisão 4: Relay seguro com 3 réplicas — `FOR UPDATE` bloqueante, sem `SKIP LOCKED`

**Decision**: o relay roda como método `@Scheduled` transacional: seleciona as N linhas mais
antigas (`ORDER BY id`) com `FOR UPDATE`, publica uma a uma **em ordem**, esperando o ack de
cada uma, e remove cada linha confirmada. Na primeira falha, encerra o lote (sem pular
linhas), faz commit das remoções já feitas e tenta de novo no próximo ciclo. Parâmetros com
padrão: intervalo 1 s, lote 100, timeout de envio 15 s.

**Rationale**: `infra/k8s/order-service-deployment.yaml` define **`replicas: 3`**, então três
relays disputam a mesma tabela. Com `FOR UPDATE` bloqueante, o segundo relay espera o
primeiro terminar o lote e depois enxerga as linhas já removidas — os lotes ficam
serializados na ordem do `id`, então a ordem por pedido se mantém entre réplicas.
`SKIP LOCKED` faria réplicas diferentes publicarem eventos diferentes do mesmo pedido ao
mesmo tempo e quebraria a ordem (FR-008). Enviar uma linha por vez e esperar o ack também
mantém a ordem sem depender de nada além da idempotência do produtor (Decisão 6).

**Alternatives considered**: `pg_try_advisory_xact_lock` para eleger um relay único —
funciona e evita bloqueio, mas é função específica de Postgres e mais uma peça; o `FOR
UPDATE` padrão já entrega a mesma garantia. Vazão em lote sem esperar cada ack — rejeitada:
o laboratório não precisa dela e ela complica a ordem sob falha parcial.

**A validar** (precisa de Postgres): dois relays concorrentes sobre a mesma tabela não perdem
nem reordenam eventos por pedido.

## Decisão 5: Payload já serializado (Jackson 3) e `StringSerializer` no produtor

**Decision**: o gravador serializa o evento com o `JsonMapper` (Jackson 3) que o Spring Boot
já autoconfigura e guarda o JSON pronto no outbox. O relay publica esse texto com
`KafkaTemplate<String, String>` e `StringSerializer` (chave = `orderId`). **Isto esclarece o
E2.1** (ver nota adicionada em [`contracts/event-contract.md`](../013-kafka-event-convention/contracts/event-contract.md)):
o que importa do contrato — família Jackson 3, JSON no fio — é preservado; só a classe
serializadora muda, porque o payload já vem serializado do outbox.

**Rationale (evidência empírica, `jshell` no classpath real)**:
- `JsonMapper` (Jackson 3) escreve `Instant` como ISO-8601 UTC
  (`"2026-09-26T10:15:30.123456Z"`), `UUID` como string e `BigDecimal` como número
  (`10.50`) — exatamente o formato do contrato do E2.1, sem nenhuma configuração extra.
- A saída do `JacksonJsonSerializer` para o mesmo objeto é **byte a byte idêntica** à do
  `JsonMapper` — logo não há diferença de formato de fio entre as duas escolhas.
- **Achado**: por padrão o `JacksonJsonSerializer` **anexa o header `__TypeId__`** com o nome
  completo da classe Java do produtor. Um consumidor em `payment-api`/`invoice-api` não tem
  essa classe; se usasse o header, falharia ou acoplaria os serviços — o oposto do
  Princípio I. Só se desliga com `spring.json.add.type.headers=false` (constante
  `ADD_TYPE_INFO_HEADERS` confirmada). Publicar texto puro elimina o problema pela raiz: nenhum
  header de tipo é emitido.
- Serializar **no momento do enqueue** (dentro da transação) significa que um erro de
  serialização vira 500 com rollback — nada é persistido nem enfileirado — em vez de uma
  "mensagem venenosa" travando a fila (edge case da spec).
- Existem `jackson-databind` 3.1.5 e 2.21.5 no classpath (a 2.x vem transitivamente, não do
  nosso código); usamos só `tools.jackson.*`, sem tocar em Jackson 2.

**Alternatives considered**: guardar campos em colunas e montar o objeto no relay para o
`JacksonJsonSerializer` — rejeitado: duplica o esquema do evento em colunas e em classe, e ainda
exigiria desligar o header de tipo.

## Decisão 6: Configuração do produtor — falhar rápido com o broker fora

**Decision**: em `application.properties`, `acks=all` e timeouts curtos:
`max.block.ms=5000`, `request.timeout.ms=5000`, `delivery.timeout.ms=10000`. Idempotência
permanece no padrão (ligada).

**Rationale (evidência empírica, `ProducerConfig` do `kafka-clients` 4.2.1)**: os padrões são
`acks=all`, `enable.idempotence=true`, `retries=2147483647`,
`max.in.flight.requests.per.connection=5` — idempotência mantém a ordem mesmo com retries, sem
custo extra. Mas `max.block.ms=60000` e `delivery.timeout.ms=120000` fariam o relay ficar até 60 s
preso no primeiro `send` com o broker fora, segurando a transação e os locks de linha do
outbox (e, com 3 réplicas, bloqueando os outros relays). Com os valores acima, um ciclo sem
broker termina em ~10 s. Restrição respeitada: `delivery.timeout.ms >= linger.ms (5) +
request.timeout.ms`.

**Verificado**: `KafkaProperties.Producer` do Boot já usa `StringSerializer` para chave e valor
por padrão; `KafkaTemplate` é autoconfigurado (`KafkaAutoConfiguration`). **A validar**: injeção
como `KafkaTemplate<String, String>` (o bean é declarado `KafkaTemplate<?, ?>`).

## Decisão 7: Tópicos declarados explicitamente (3 partições, 1 réplica)

**Decision**: beans `NewTopic` (via `TopicBuilder`) para `order.created`, `order.confirmed` e
`order.cancelled`, com **3 partições** e **1 réplica**; a chave de toda mensagem é o `orderId`.

**Rationale**: declarar os tópicos deixa a convenção do E2.1 executável em vez de depender do
`auto.create.topics.enable` do broker (que provedores gerenciados costumam desligar —
Princípio IV). Com 1 partição a ordem por pedido seria trivialmente verdadeira e nenhum teste
provaria nada; com 3 partições e chave `orderId` a garantia da Decisão 4 é exercitada de
verdade. Réplica 1 funciona em qualquer cluster (só sem redundância) e o laboratório tem um
único broker. `KafkaAdmin` (Boot: `spring.kafka.admin.auto-create=true` por padrão, confirmado
no bytecode) cria só o que falta, então as 3 réplicas do `order-api` criando os mesmos tópicos
é seguro.

**Limitação conhecida**: `spring.kafka.admin.fail-fast` é `false` por padrão — se o broker
estiver fora no boot, o serviço sobe e loga o aviso, e os tópicos não são criados até o
próximo restart. Nesse caso o primeiro `send` cairia no auto-create do broker (1 partição), o
que preserva a ordem mas muda a topologia. Aceito para o laboratório; a alternativa
(provisionar tópicos na infraestrutura) fica para a Fase 4.

**Verificado**: nenhum manifest (`docker-compose.yml`, `infra/k8s/*.yaml`) sobrescreve
`auto.create`/`num.partitions` (usam os padrões do broker `apache/kafka:4.2.0`).
**A validar**: `kafka-topics.sh --describe` mostrando 3 partições nos 3 tópicos.

## Decisão 8: Observabilidade — logs estruturados e `trace_id` de origem

**Decision**: (a) log INFO no enqueue (dentro da requisição, então já sai com `traceId`/`spanId`
pelo MDC) com `orderId`, `eventId`, `eventType`, `topic`; (b) log INFO no envio confirmado
(com partição/offset) e WARN/ERROR na falha, no relay; (c) o `traceId` corrente do MDC é
gravado em `outbox_events.trace_id` no enqueue e reaparece nos logs do relay como campo
`originTraceId`. **Não** ligar `spring.kafka.template.observation-enabled` (padrão `false`).

**Rationale**: o relay roda numa thread agendada, sem trace ativo — sem o `trace_id` gravado, o
FR-010 ("correlacionado com `traceId`") não seria atendido nos logs de envio. A chave do MDC
é `traceId` (confirmada no bytecode do `spring-boot-micrometer-tracing`). Ligar a observação
do `KafkaTemplate` propagaria contexto de trace nos headers Kafka, que é o escopo do E6.4,
fora deste item (FR-015).

## Decisão 9: Estratégia de testes — sem dependência nova

**Decision**: nenhum artifact novo no `pom.xml` (`spring-boot-starter-kafka`, JPA, Flyway e os
Testcontainers de Postgres já existem). Testes:
- Unidade: `OrderServiceTest` (adaptado: gravador mockado, leitura com lock), gravador
  (formato do payload vs contrato), relay (`KafkaTemplate` mockado: ordem, remoção após ack,
  parada na primeira falha).
- Integração (`OrderApiApplicationTests`, Postgres via Testcontainers como no item 1.11,
  `@MockitoBean KafkaTemplate`): intervalo do relay alto nos testes, então as linhas do outbox
  ficam visíveis e o relay é chamado manualmente — cobre FR-004 (rejeições não geram linha),
  FR-005 (linha commitada junto com a transição), FR-006 (2xx com `KafkaTemplate` falhando) e
  FR-009 (confirm×cancel concorrentes: 1×200, 1×409, 1 evento).
- Kafka real: validação empírica manual no [quickstart](./quickstart.md), como nos itens da
  Fase 1. A suíte Testcontainers Kafka é o E2.6 (FR-015).

`./mvnw test` continua autossuficiente (FR-014): só exige runtime Docker, como desde o item
1.11.

## Decisão 10: Pendências que dependem de Docker (validar no início da implementação)

O daemon Docker estava parado durante o planejamento. Antes de considerar as decisões acima
fechadas, a implementação MUST confirmar empiricamente, com Docker ligado:

1. SQL de lock emitido pelo Hibernate 7 no `PESSIMISTIC_WRITE` e teste confirm×cancel real.
2. `ddl-auto=validate` aceitando `payload TEXT` (senão aplicar a mitigação da Decisão 3).
3. Injeção de `KafkaTemplate<String, String>` a partir do bean `KafkaTemplate<?, ?>`.
4. Dois relays concorrentes sobre a mesma tabela sem perda nem reordenação.
5. Tópicos criados com 3 partições (`kafka-topics.sh --describe`) e mensagens chegando com
   chave `orderId` e sem header `__TypeId__`.
6. Cenários de broker parado/religado e de `kill -9` entre persistir e publicar
   ([quickstart](./quickstart.md)).

## Riscos e limitações conhecidas

- **Bloqueio na cabeça da fila**: um erro *permanente* do broker para uma mensagem (ex.:
  registro grande demais, tópico inválido) trava todas as seguintes, porque pular linhas
  quebraria a ordem. Os payloads são minúsculos e gerados por código nosso, então o risco é
  baixo; a falha fica visível a cada ciclo (FR-010). Estratégia de estacionamento/"parking"
  fica fora deste item.
- **Ordem só por pedido**, não global (assumido na spec): o `id` do outbox pode ter lacunas e
  a ordem de commit entre pedidos diferentes não é garantida.
- **Constitution**: a seção "Fases de Evolução" foi removida na v3.3.0 (as fases passaram a
  viver só no ROADMAP.md); a pendência anotada durante o planejamento ficou resolvida.

## Validações empíricas (implementação, 2026-10-04)

Docker ligado; Postgres 15 e Kafka `apache/kafka:4.2.0` reais; serviço rodado com
`spring-boot:run`. Pendências da Decisão 10:

1. **Lock** — Hibernate 7 emite `select ... from orders ... for no key update of o1_0`
   (confirm/cancel) e `select ... from outbox_events ... order by id fetch first ? rows only
   for no key update of oe1_0` (relay). `FOR NO KEY UPDATE` conflita consigo mesmo no Postgres,
   então serializa. Teste de integração confirm×cancel: 1×200, 1×409, 1 evento final. No
   Kafka real, 5 pares concorrentes: 5×(200+409) e exatamente 1 de
   `OrderConfirmed`/`OrderCancelled` por pedido. ✅
2. **`ddl-auto=validate` com `payload TEXT`** — contexto sobe e valida sem mitigação
   (`@JdbcTypeCode` não foi necessário). ✅
3. **`KafkaTemplate<String, String>`** — injeção direta funciona contra o bean autoconfigurado
   `KafkaTemplate<?, ?>` (serviço subiu e publicou). ✅
4. **Dois relays concorrentes** — teste com 20 pedidos (40 eventos), 2 threads: 40 envios,
   outbox vazio, `order.created` antes de `order.confirmed` por pedido. ✅
5. **Tópicos e mensagens** — `kafka-topics.sh --describe`: `PartitionCount: 3`,
   `ReplicationFactor: 1` nos 3 tópicos. Consumidor com `print.headers=true`: `NO_HEADERS`
   (nenhum `__TypeId__`) e chave = `orderId`. ✅
6. **Broker parado/religado** — com o Kafka parado, 3 `create` + 1 `confirm` responderam
   201/200; o outbox acumulou 5 linhas; WARN do relay com `orderId`/`eventId`/`eventType`/
   `originTraceId`. Ao religar, o outbox esvaziou em ~12 s e os eventos chegaram (criado
   publicado antes do confirmado). **`kill -9`** com o broker parado: evento do pedido
   permaneceu no outbox e foi publicado após religar broker + serviço. ✅

Observação: logo após religar o broker, o primeiro `kafka-console-consumer` (timeout 5 s)
voltou vazio por warm-up de grupo; repetido, trouxe tudo — não é problema do produtor.

Outras verificações: Compose e k8s já definem `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
para o `order-api`; nenhuma alteração de infraestrutura foi necessária (Princípio IV).
`./mvnw clean verify`: 42 testes, `BUILD SUCCESS`; `pmd:pmd`: 0 violações.
