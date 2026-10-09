# Research: Estratégia de Erro de Consumo (Retry Limitado e DLT)

Verificações feitas em 2026-10-04 contra o classpath real do `payment-api` (o `invoice-api` usa o
mesmo BOM: Spring Boot 4.1.1, `spring-kafka` 4.1.1, `kafka-clients` 4.2.1), via `javap` e `jshell`.
Esta feature **substitui** a política de erro interina do E2.3/E2.4 (Decisão 4 de
[`specs/015-payment-kafka-consumer-producer/research.md`](../015-payment-kafka-consumer-producer/research.md):
retry ilimitado de 1 s para falha transitória e log+descarte para mensagem inválida). Tudo mais
(outbox, idempotência, atomicidade, tópicos, observabilidade) é herdado e não muda. Pendências que
dependem de Docker estão na Decisão 8.

## Decisão 1: `DefaultErrorHandler` com backoff exponencial limitado + `DeadLetterPublishingRecoverer`

**Decision**: em cada serviço consumidor, o bean `CommonErrorHandler` (que o Boot aplica sozinho ao
container) passa a ser um `DefaultErrorHandler` com:
- `ExponentialBackOffWithMaxRetries(maxRetries)` com `initialInterval`, `multiplier` e
  `maxInterval` configuráveis (padrão: 4 retentativas, 1 s, ×2, teto 10 s ⇒ esperas 1+2+4+8 s ≈ 15 s);
- um `DeadLetterPublishingRecoverer` como recoverer: esgotadas as tentativas, a mensagem vai ao DLT e
  o consumo **segue** para a próxima.

**Rationale (evidência empírica)**: todas as classes existem no classpath (`javap`):
`DeadLetterPublishingRecoverer(KafkaOperations, BiFunction<ConsumerRecord, Exception, TopicPartition>)`,
`ExponentialBackOffWithMaxRetries(int)` com `setInitialInterval/setMultiplier/setMaxInterval`,
`DefaultErrorHandler.setRetryListeners(...)`. O recoverer anexa automaticamente os headers de
diagnóstico `kafka_dlt-original-topic/-partition/-offset/-consumer-group/-timestamp` e
`kafka_dlt-exception-fqcn/-cause-fqcn/-message/-stacktrace` (constantes `KafkaHeaders.DLT_*`
confirmadas) — atende FR-004 sem código próprio.

**Alternatives considered**: `@RetryableTopic` (retry em tópicos separados, não-bloqueante) —
rejeitado: cria uma cadeia de tópicos de retry por serviço, muito além do que o laboratório precisa
e conflita com a ordem por pedido; o retry bloqueante curto (≈15 s) é suficiente e simples.
`FixedBackOff` limitado — rejeitado: o enunciado pede espera crescente.

## Decisão 2: Destino `<tópico>.dlt` com partição escolhida pela chave

**Decision**: o resolvedor de destino devolve `new TopicPartition(record.topic() + ".dlt", -1)`:
nome em minúsculas (convenção `<tópico-de-origem>.dlt`) e **partição `-1`**, para o produtor escolher a
partição pela **chave** da mensagem. A chave original é repassada pelo recoverer, então mensagens do
mesmo pedido caem na mesma partição do DLT (FR-006). Cada serviço declara o seu DLT com `NewTopic`
(3 partições, 1 réplica): `order.created.dlt` no `payment-api`, `payment.reserved.dlt` no
`invoice-api`.

**Rationale**: o padrão do recoverer (`<tópico>-dlt` e **mesma partição** da origem) exigiria o DLT
com pelo menos as mesmas partições e usaria hífen, fugindo da convenção do E2.1 (`.` como separador,
minúsculas). `verifyPartition=true` (padrão, confirmado) só valida partições explícitas (≥ 0), então
`-1` não é verificado e não falha se a contagem divergir.

## Decisão 3: Classificação — inválido é permanente; todo o resto é transitório

**Decision**: o listener deixa de "logar e retornar" para mensagem inválida e passa a lançar
`InvalidMessageException` (exceção própria, com a causa original: erro de parse, campo ausente,
valor nulo, `amount <= 0`). O handler registra `addNotRetryableExceptions(InvalidMessageException.class)`
(método confirmado em `ExceptionClassifier`): essa falha vai **direto** ao recoverer, sem retries
(FR-002). Qualquer outra exceção (falha de banco, violação de constraint na corrida, bug) é tratada como
transitória: tenta N vezes e, se persistir, vai ao DLT — **nunca é descartada** (FR-003).

**Rationale**: uma só regra de saída (o DLT) elimina o caminho de descarte silencioso do E2.3/E2.4.
Tratar falha desconhecida como transitória é a escolha segura: no pior caso a mensagem cumpre o
retry curto e fica estacionada, recuperável.

## Decisão 4: DLT indisponível não perde a mensagem

**Decision**: manter os padrões `failIfSendResultIsError=true` e `waitForSendResultTimeout=30s` do
recoverer (ambos confirmados por reflexão) e `resetStateOnRecoveryFailure` padrão do
`DefaultErrorHandler`: se o envio ao DLT falhar, o recoverer lança, o handler considera a recuperação
falha e **reentrega o mesmo registro** (reinicia o ciclo de tentativas) em vez de avançar o offset.

**Rationale**: dá o FR-007 sem código: a mensagem só deixa a partição quando foi processada ou
efetivamente publicada no DLT. Com os timeouts curtos do produtor já configurados
(`max.block.ms=5000`, `delivery.timeout.ms=10000`), a falha de envio ao DLT é detectada em segundos.

## Decisão 5: Observabilidade de cada retry e de cada envio ao DLT

**Decision**: um `RetryListener` registrado no handler loga (`log.atWarn()/atError()`), de forma
estruturada: `failedDelivery` (tentativa N e causa), `recovered` (enviada ao DLT) e `recoveryFailed`
(DLT indisponível). Os campos: `topic`, `partition`, `offset`, `attempt`, causa, e — quando legíveis —
`orderId` e `eventId`, extraídos do valor por um parse tolerante (falha de parse nunca lança). O
`traceId` vem do MDC (a observação do listener já está ligada desde o E2.3, FR-008).

**Rationale**: o `RetryListener` recebe só o `ConsumerRecord` cru; o parse tolerante garante que mesmo
mensagem ilegível gere log com origem (`topic/partition/offset`), e que a legível traga `orderId`/`eventId`.
**A validar**: que o log de retry traga `traceId` (o handler roda na thread do listener, dentro do span).

## Decisão 6: Conexão com o banco falha rápido — tornar o retry realmente limitado

**Decision**: configurar `spring.datasource.hikari.connection-timeout=5000` (5 s) nos dois consumidores.

**Rationale (evidência do E2.3)**: durante a validação com o Postgres parado, o Hikari bloqueou ~30 s por
tentativa (padrão de `connectionTimeout`), então só se viu **uma** tentativa de consumo em ~15 s e o
tempo total de uma mensagem seria ≈5 × 30 s = 150 s — contrariando o SC-002 (tempo limitado a "cerca
de 30 s") e arriscando estourar `max.poll.interval.ms` (5 min) com poucas mensagens. Com 5 s por
tentativa, o pior caso de uma mensagem fica em ≈ 5 × 5 s + 15 s ≈ 40 s. O ajuste vale para o pool inteiro
(inclusive o REST) e é inofensivo: com banco saudável a conexão sai em milissegundos.
**A validar**: tempo real até o DLT com o Postgres parado.

## Decisão 7: Propriedades de configuração e testes

**Decision**: `ConsumerRetryProperties` (`@ConfigurationProperties("consumer.retry")`, record com
`maxRetries`=4, `initialIntervalMs`=1000, `multiplier`=2.0, `maxIntervalMs`=10000) em cada serviço.
Testes sem dependência nova e sem Testcontainers Kafka (E2.6): o handler real é exercitado com
`DefaultErrorHandler.handleOne(...)` usando `Consumer`/`MessageListenerContainer` mockados e um
`KafkaTemplate` mockado — verifica (a) falha transitória tenta N vezes com esperas crescentes e só então
chama o DLT; (b) `InvalidMessageException` chama o DLT na 1ª vez, sem retry; (c) o `ProducerRecord` do DLT
tem tópico `<origem>.dlt`, chave e valor idênticos e os headers de diagnóstico; (d) falha no envio ao DLT
não considera a mensagem recuperada. Integração com Kafka real: validação empírica no
[quickstart](./quickstart.md).

## Decisão 8: Pendências que dependem de Docker (validar na implementação)

1. Tópicos `order.created.dlt` e `payment.reserved.dlt` com 3 partições, 1 réplica.
2. Mensagem ilegível (e sem campo obrigatório) → DLT **sem retries**, chave/valor idênticos, headers
   `kafka_dlt-*` presentes; as mensagens seguintes seguem sendo processadas.
3. Postgres parado além do limite → mensagens no DLT sem perda; tempo até o DLT ≈ 40 s (Decisão 6);
   serviço retoma sozinho; reprocessar do DLT converge sem duplicar.
4. Kafka (DLT) indisponível no momento de estacionar → mensagem não perdida (reentregue depois).
5. Logs estruturados de cada retry e do envio ao DLT com `topic/partition/offset/attempt/causa` e
   `traceId`; `orderId`/`eventId` quando legíveis.
6. Procedimento de reprocessamento manual (DLT → tópico de origem) executado de ponta a ponta.

## Riscos e limitações conhecidas

- **Ordem por pedido**: ao estacionar uma mensagem, as seguintes seguem; a ordem entre a estacionada e as
  posteriores não é garantida (efeito aceito do retry limitado). A idempotência (E2.3/E2.4) garante que
  o reprocessamento não duplica.
- **Fila bloqueada até ≈40 s** por mensagem em falha total do banco (limite configurável); durante uma
  queda longa, cada mensagem consome seu ciclo antes de ir ao DLT.
- **DLT sem consumidor/limpeza**: segue a retenção padrão do broker; gestão do DLT é manual e fora de
  escopo.
- **Duplicação consciente** da configuração do handler entre `payment-api` e `invoice-api` (Princípio I).

## Validações empíricas (implementação, 2026-10-04)

Docker ligado; Postgres 15 e Kafka `apache/kafka:4.2.0` reais; os três serviços rodando. Linha de base:
`./mvnw clean verify` com `BUILD SUCCESS` nos dois consumidores (último estado commitado dos itens E2.3/E2.4).
Pendências da Decisão 8:

1. **Tópicos** — `order.created.dlt` e `payment.reserved.dlt` criados com `PartitionCount: 3`,
   `ReplicationFactor: 1`. ✅
2. **Mensagem inválida → DLT sem retry** — em `order.created` (payment-api) e `payment.reserved`
   (invoice-api): `lixo-nao-json`, `{"eventId":"x","amount":5}`, `{"amount":5}` e `amount` 0 foram todos ao DLT com
   **chave e valor idênticos**; só a tentativa 1 foi logada (nenhum retry); o log de envio ao DLT trouxe
   `traceId`, origem (`topic/partition/offset`) e `orderId`/`eventId` apenas quando legíveis; as mensagens
   válidas seguintes foram processadas (`RESERVED`/`ISSUED`) e os serviços ficaram `UP`. ✅
3. **Queda curta do banco** (~8 s, 1 mensagem): 2 tentativas com falha e sucesso na 3ª; **nada** no DLT;
   pagamento `RESERVED`. ✅
4. **Queda longa do banco** (3 mensagens): cada uma fez as 5 tentativas (t = +0, +7, +14, +23, +36 s: espera
   1/2/4/8 s somada a ~5 s do `connection-timeout` do Hikari) e foi ao DLT em ≈ 36 s, sem perda; ao religar,
   uma mensagem **nova** foi processada normalmente (o consumo retomou sozinho). O tempo por mensagem fica
   dentro do previsto (≈ 40 s). ✅
5. **DLT indisponível** — parar o Kafka no meio do ciclo de retry (com o banco parado) e religar tudo: a mensagem
   **não foi perdida nem estacionada**; foi processada quando banco e Kafka voltaram (1 pagamento). O caminho
   "envio ao DLT falha" em si está coberto pelo teste do handler (`handleOne` devolve "não recuperada"
   quando o `send` do DLT falha → a mensagem é reentregue); não foi possível provocá-lo no serviço real, porque
   sem Kafka nenhuma mensagem chega a ele. Parcialmente validado. ⚠️
6. **Reprocessamento manual (DLT → origem)** — lidas chave+valor das 3 mensagens estacionadas e reenviadas ao
   tópico de origem: 1 pagamento por pedido (`RESERVED`, `FAILED`, `RESERVED`), 1 evento de saída cada;
   reenviar as mesmas 3 de novo → 3 logs `Duplicate OrderCreated ignored`, 0 duplicatas; uma mensagem ainda
   inválida (`bad1`) reenviada **voltou ao DLT**. O procedimento do quickstart (cenário 6) funcionou como
   escrito. ✅
7. **Observabilidade** — cada retry sai com `attempt` (1..5), `topic/partition/offset`, causa e `traceId`;
   o envio ao DLT sai como `Message sent to DLT` com `dltTopic`. ✅

`./mvnw clean verify` em cada consumidor: 57 testes, `BUILD SUCCESS`; `pmd:pmd`: 0 violações (após corrigir 3
apontamentos do PMD no `DltRetryListener`/`InvalidMessageException`); `order-api` e todos os `pom.xml` sem
alteração.

Achados de implementação: (a) `DefaultErrorHandler.handleOne` pode ser exercitado em teste de unidade com
`Consumer`, `MessageListenerContainer` e `KafkaTemplate` mockados e esperas de 1 ms — sem Testcontainers Kafka
(que é o E2.6); (b) o `RetryListener.failedDelivery` também é chamado uma vez para falha **não-retentável**
(tentativa 1) — por isso a mensagem de log é "will retry or park in DLT"; (c) o `uuidgen` do macOS gera UUIDs em
maiúsculas, enquanto o banco guarda minúsculas — normalizar ao consultar nos scripts de validação.
