# Research: invoice-api como Consumidor e Produtor Kafka

O `invoice-api` usa o mesmo BOM do `payment-api` (Spring Boot 4.1.1, `spring-kafka` 4.1.1,
`kafka-clients` 4.2.1, Jackson 3, Hibernate 7) e as mesmas dependências (`pom.xml` idêntico em
Kafka/JPA/Flyway/Testcontainers). Por isso **todas as decisões do lado produtor e do lado
consumidor já verificadas empiricamente** são reaproveitadas, não refeitas:

- Produtor/outbox/relay → [`specs/014-order-kafka-producer/research.md`](../014-order-kafka-producer/research.md)
  (Decisões 1–8).
- Consumidor, idempotência, política de erro, tópicos e observabilidade →
  [`specs/015-payment-kafka-consumer-producer/research.md`](../015-payment-kafka-consumer-producer/research.md)
  (Decisões 1–9, mais as "Validações empíricas", que confirmam cada uma contra Kafka/Postgres reais).

Este documento registra só o que **muda** para o `invoice-api` e as pendências a validar.

## Decisão 1: Mesmo desenho do E2.3, copiado para o invoice-api (Princípio I)

**Decision**: o `invoice-api` ganha o seu próprio outbox transacional (`outbox_events`), relay
`@Scheduled` (`FOR UPDATE` bloqueante, ack por mensagem, remoção após ack, lote encerrado na
primeira falha), escritor `MANDATORY`, `StringSerializer` + chave `orderId`, sem header
`__TypeId__`; consumidor `@KafkaListener` com valor `String` + parse Jackson 3 manual;
`DefaultErrorHandler` com `FixedBackOff(1000, UNLIMITED_ATTEMPTS)`; processamento de cada
mensagem em **uma única transação** (dedup + nota + outbox). As classes são **copiadas** do
`payment-api` para o pacote `com.orderslab.invoice_api` — nunca compartilhadas (FR-013).

**Rationale**: os requisitos (FR-004 a FR-011) são os mesmos do E2.3, e a solução foi validada
(incluindo broker parado, `kill -9` e banco parado). O `k8s` define `replicas: 3` também para o
`invoice-api`, então o argumento do `FOR UPDATE` bloqueante e das constraints como rede de
segurança contra a corrida entre réplicas continua valendo. A duplicação de código é o preço
consciente da independência (esta é a terceira cópia do outbox).

## Decisão 2: O que muda — entrada, regra, campos e nomes

| Aspecto | E2.3 (`payment-api`) | E2.4 (`invoice-api`) |
|---|---|---|
| Tópico consumido | `order.created` | `payment.reserved` (**não** `payment.failed`) |
| Grupo | `payment-api` | `invoice-api` |
| Campos lidos | `eventId`, `orderId`, `amount` | `eventId`, `orderId`, `paymentId` (UUID), `amount` |
| Regra | `amount > 1000.00` → falha | `amount > 500.00` → falha (`invoice.issuance.limit`) |
| Motivo da falha | `AMOUNT_LIMIT_EXCEEDED` | `AMOUNT_ABOVE_ISSUANCE_LIMIT` |
| Saída de sucesso | `PaymentReserved` → `payment.reserved` | `InvoiceIssued` → `invoice.issued` (+ `invoiceId`, `paymentId`, `amount`) |
| Saída de falha | `PaymentFailed` → `payment.failed` | `InvoiceFailed` → `invoice.failed` (+ `paymentId`, `reason`) |
| Status novo | `FAILED` | `FAILED` (e nasce `ISSUED` no sucesso) |
| Tópicos declarados | `order.created`, `payment.*` | `payment.reserved`, `invoice.issued`, `invoice.failed` |

O limite de emissão (500.00) é menor que o de pagamento (1000.00) **de propósito**: pedidos entre
500.00 e 1000.00 têm pagamento reservado e nota que falha, exercitando o caminho de falha da nota
ponta a ponta sem tocar nos outros serviços (spec, Assumptions).

## Decisão 3: Idempotência e "uma nota por pedido" — constraints em `invoices`

**Decision**: a tabela `invoices` ganha `source_event_id UUID` (nula), índice único em
`source_event_id` (FR-004) e índice **parcial** único em `order_id WHERE source_event_id IS NOT
NULL` (FR-005); o `invoice-api` REST segue podendo repetir `orderId` (FR-012). Nota emitida a
partir de evento nasce `ISSUED` (sem passar por `PENDING`); a que falha nasce `FAILED`. A nota
**é** o marcador de "evento tratado" — sem tabela de eventos processados (mesma Decisão 3 do
E2.3). `issue`/`cancel` de nota `FAILED` ou já `ISSUED` caem no
`InvalidStatusTransitionException` existente (409), sem código novo.

**Verificação do código atual**: `InvoiceStatus` hoje é `PENDING | ISSUED | CANCELLED`;
`Invoice.paymentId` é `String`; `invoices.status VARCHAR(20)` comporta `FAILED`; `invoices.payment_id`
é `VARCHAR(255)`. O contrato REST não expõe nada novo além do valor `"FAILED"`.

## Decisão 4: Tolerância do consumidor e validação de entrada

> **Atualizada pelo E2.5** ([spec 017](../017-consumer-retry-dlt/spec.md)): mensagem inválida agora vai ao DLT
> `payment.reserved.dlt` (antes: log e descarte) e a falha transitória usa retry limitado (antes: sem limite).

`PaymentReservedMessage` (record de entrada, `@JsonIgnoreProperties(ignoreUnknown = true)`) exige
`eventId`, `orderId`, `paymentId` e `amount > 0`; ausência ou `amount <= 0` é erro **permanente**
(log `ERROR` e descarte, FR-011); `orderId`/`paymentId` não-UUID falham no parse (também
permanente). Falha de banco é transitória (retry ilimitado). Retry limitado e DLT são o E2.5.

## Decisão 5: Estratégia de testes

Igual à do E2.3 (Decisão 9): sem dependência nova, sem Testcontainers Kafka. Unidade
(regra, eventos vs contrato, listener, escritor/relay, política de erro) + integração com Postgres
Testcontainers e `KafkaTemplate` mockado chamando o processador diretamente (reservado/falhou,
reentrega, dois `eventId` no mesmo pedido, rollback conjunto, broker parado, corrida, contrato REST,
dois relays). Kafka real validado empiricamente no [quickstart](./quickstart.md), agora incluindo o
fluxo `order-api → payment-api → invoice-api`.

## Decisão 6: Pendências que dependem de Docker (validar na implementação)

1. Migração `V2` aplica sobre um `invoice_db` com dados do `V1`; `ddl-auto=validate` aceita
   `source_event_id` e `payload TEXT`.
2. Injeção de `KafkaTemplate<String, String>` e leitura de `payment.reserved` desde o início
   (grupo novo): o backlog do E2.3 vira notas.
3. Tópicos com 3 partições, chave `orderId`, sem header `__TypeId__`.
4. Fluxo ponta a ponta com os três serviços: valor baixo → nota emitida; valor entre 500 e 1000
   → pagamento reservado + nota que falha; valor acima de 1000 → pagamento falha e **nenhuma**
   nota.
5. Reentrega, ilegíveis, broker parado, `kill -9` entre persistir e publicar, banco parado —
   mesmos cenários do E2.3, agora no `invoice-api`.
6. `traceId` nos logs do consumo e `originTraceId` no relay.

## Riscos e limitações conhecidas

- **Bloqueio na cabeça da partição** com retry ilimitado para falha transitória: aceito (E2.3);
  estacionamento/DLT é o E2.5.
- **Terceira cópia do código de outbox** entre serviços (Princípio I).
- **Ordem só por pedido**; `PaymentReserved` e `InvoiceIssued` estão em tópicos diferentes — a ordem
  entre tópicos é a de publicação do relay.
- Se o `payment-api` re-emitir um `PaymentReserved` com `eventId` novo para o mesmo pedido, a
  nota não duplica (índice parcial por `order_id`), por construção.

## Validações empíricas (implementação, 2026-10-04)

Docker ligado; Postgres 15 e Kafka `apache/kafka:4.2.0` reais; **os três serviços** rodando
(`order-api` 8081, `payment-api` 8082, `invoice-api` 8083). Linha de base: `./mvnw clean verify` com
`BUILD SUCCESS` antes da feature. Pendências da Decisão 6:

1. **Migração `V2` e `ddl-auto=validate`** — aplicou sobre um `invoice_db` já existente;
   `source_event_id` e `payload TEXT` validaram sem mitigação. ✅
2. **`KafkaTemplate<String, String>` e backlog** — injeção direta funcionou; ao subir, o
   `invoice-api` leu os 20 `PaymentReserved` do tópico (E2.3) e criou 19 notas `ISSUED` e 1 `FAILED`
   (o pedido de 1000 da validação anterior, acima do limite de 500). ✅
3. **Tópicos** — `payment.reserved`, `invoice.issued` e `invoice.failed` com `PartitionCount: 3`,
   `ReplicationFactor: 1`; mensagens com chave = `orderId` e `NO_HEADERS` (sem `__TypeId__`). ✅
4. **Ponta a ponta** (`order-api → payment-api → invoice-api`): 10.50 e 500 (exato) → pagamento
   `RESERVED` e nota `ISSUED` (`InvoiceIssued`); 750 → pagamento `RESERVED` e nota `FAILED`
   (`InvoiceFailed`, `AMOUNT_ABOVE_ISSUANCE_LIMIT`); 1500 → pagamento `FAILED` (`PaymentFailed`) e
   **nenhuma nota nem evento de nota**. Os três outboxes esvaziaram em segundos. ✅
5. **Reentrega/ilegíveis/falhas** (mesmos cenários do E2.3, agora no `invoice-api`):
   - **Reentrega**: o mesmo `PaymentReserved` 2× e um terceiro com `eventId` novo e o mesmo
     `orderId` → 3 logs "Duplicate PaymentReserved ignored"; continua 1 nota e 1 evento.
   - **Ilegíveis**: `lixo-nao-json`, `{"eventId":"x",...}`, `{"amount":5}` e um com `amount` 0 → 4
     logs `ERROR` de descarte com topic/partition/offset/trecho; o `PaymentReserved` válido seguinte
     virou nota `ISSUED`; serviço `UP`.
   - **Broker parado**: 3 pedidos criados com o Kafka fora (`invoice-api` `UP`); ao religar, os
     três outboxes (order/payment/invoice) esvaziaram em ~18 s e as 3 notas foram decididas
     (ISSUED, FAILED, ISSUED).
   - **`kill -9` entre persistir e publicar**: com o relay desligado por config
     (`OUTBOX_RELAY_INTERVAL_MS=3600000`) a nota ficou `ISSUED` com a linha no outbox e **0** no
     tópico; após `kill -9` e restart normal, 1 evento no tópico e 1 nota.
   - **Postgres parado** com 3 `PaymentReserved` injetados: ao religar, as 3 viraram notas
     (ISSUED, FAILED, ISSUED) em ~8 s, **nenhuma descartada**, outbox vazio. Observação: durante a
     queda o endpoint de health do actuator reportou `DOWN` (indicador de banco), mas o processo
     seguiu vivo e retomou sozinho.
6. **Observabilidade** — por mensagem, `PaymentReserved received`, `Event enqueued in outbox` e
   `Invoice decided from PaymentReserved` saem com o **mesmo** `traceId`, e o log do relay
   (`Event published to Kafka`) traz o seu próprio `traceId` e o do consumo em `originTraceId`. ✅

Outras verificações: Compose e k8s já definem `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` para o
`invoice-api` (k8s com `replicas: 3`); nenhuma alteração de infraestrutura foi necessária (Princípio
IV). `./mvnw clean verify`: 52 testes, `BUILD SUCCESS`; `pmd:pmd`: 0 violações; `order-api`,
`payment-api` e `pom.xml` do `invoice-api` sem alteração.

Achados de implementação: (a) o `invoice-api` foi montado **copiando** o `payment-api` por script
(pacote e nomes trocados) e conferido que não sobrou nenhum import cruzado (Princípio I); (b) a
armadilha do zsh (`for x in $VAR` não separa palavras) apareceu de novo nos scripts de validação —
usar `$(cat arquivo)` com um id por linha.
