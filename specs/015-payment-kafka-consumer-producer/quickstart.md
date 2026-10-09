# Quickstart: validar o payment-api como consumidor e produtor Kafka

Guia de validação empírica contra Postgres e Kafka reais (FR-014). Contratos em
[contracts/payment-events.md](./contracts/payment-events.md); esquema em
[data-model.md](./data-model.md).

## Pré-requisitos

- Docker em execução.
- Infra: `docker compose -f infra/docker-compose.yml up -d postgres-api kafka`
- `order-api` na porta 8081 e `payment-api` na 8082 (dois terminais), ambos com os padrões do
  `application.properties` (Kafka em `localhost:9092`):
  `cd order-api && SERVER_PORT=8081 ./mvnw spring-boot:run` e
  `cd payment-api && SERVER_PORT=8082 ./mvnw spring-boot:run`

Consumir um tópico (de dentro do contêiner do Kafka):
`docker exec kafka-orderslab /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server
localhost:9092 --topic <tópico> --from-beginning --timeout-ms 8000 --formatter-property
print.key=true --formatter-property print.headers=true`

Banco: `docker exec postgres-api psql -U payment_user -d payment_db -c "<sql>"`

## Cenário 1 — Reservado (US1, SC-001/SC-002)

`POST localhost:8081/api/orders` `{"customerId":"c1","amount":10.50}`; anotar o `id`.
**Esperado**: em até 10 s, um `PaymentReserved` em `payment.reserved` com chave = `orderId`,
envelope completo, `paymentId`/`amount`, **sem** header `__TypeId__`; `GET
localhost:8082/api/payments` mostra um pagamento `RESERVED` desse `orderId`.

## Cenário 2 — Falhou (US1)

Pedido com `amount` 1500. **Esperado**: um `PaymentFailed` em `payment.failed`
(`reason":"AMOUNT_LIMIT_EXCEEDED"`) e um pagamento `FAILED`. Pedido com `amount` exatamente
`1000` → `RESERVED`. `confirm`/`cancel` do pagamento `FAILED` → 409.

## Cenário 3 — Tópicos e partições (Decisão 7)

`kafka-topics.sh --bootstrap-server localhost:9092 --describe` para `order.created`,
`payment.reserved`, `payment.failed`. **Esperado**: `PartitionCount: 3`, `ReplicationFactor: 1`
nos três — inclusive subindo o `payment-api` **antes** do `order-api` numa execução limpa.

## Cenário 4 — Reentrega não duplica (US2.1, SC-003)

Reenviar manualmente o mesmo `OrderCreated` (mesmo `eventId`) ao tópico com o console-producer:
`kafka-console-producer.sh --topic order.created --property parse.key=true
--property key.separator=:` colando `orderId:<json copiado do consumo do cenário 1>`.
**Esperado**: log `duplicate` no `payment-api`; continua 1 pagamento e 1 evento de saída para
esse `orderId`. Repetir com um `eventId` novo e o mesmo `orderId` → ainda 1 pagamento.

## Cenário 5 — Broker fora do ar (US2.3, SC-004)

`docker stop kafka-orderslab`; (o `order-api` continua aceitando pedidos e guarda no seu outbox).
Religar o broker. **Esperado**: o `order-api` publica, o `payment-api` consome e decide, e os
eventos chegam; o `payment-api` permaneceu saudável (`/actuator/health` = `UP`) o tempo todo.
Variante: parar o Kafka **depois** de o `payment-api` decidir — `outbox_events` do `payment_db`
acumula e esvazia ao religar.

## Cenário 6 — Queda entre persistir e publicar (US2.4, SC-005)

Com o broker parado, injetar o `OrderCreated` (cenário 4 após religar só o necessário) ou criar
pedido e deixar o `payment-api` decidir; `kill -9` no `payment-api` com linhas em
`outbox_events`; religar broker e serviço. **Esperado**: os eventos pendentes são publicados.

## Cenário 7 — Banco do payment-api indisponível (Decisão 4)

`docker stop postgres-api`; criar pedidos no `order-api` não é possível (mesmo Postgres) — por
isso injetar `OrderCreated` direto no tópico (cenário 4). **Esperado**: o consumo repete a cada
1 s com log de erro e **nenhum registro é descartado**; ao `docker start postgres-api`, todas as
mensagens são processadas, um pagamento por pedido.

## Cenário 8 — Mensagem ilegível (FR-011)

Injetar `lixo-nao-json` e um JSON sem `orderId` em `order.created`, seguidos de um `OrderCreated`
válido. **Esperado**: dois logs `ERROR` de descarte (topic/partition/offset) e o válido é
processado normalmente; o serviço segue de pé.

## Cenário 9 — Ordem e observabilidade (US3, SC-006/SC-007)

Criar vários pedidos em sequência; por `orderId`, os eventos aparecem na ordem das decisões. Nos
logs ECS localizar por `orderId` o log de recebimento (com `traceId`), o de decisão e o de envio
(com partição/offset e `originTraceId`).

## Cenário 10 — Suíte automatizada e contrato HTTP (FR-015, SC-008)

`cd payment-api && ./mvnw clean verify` com só o Docker ligado → `BUILD SUCCESS`, PMD limpo,
`PaymentControllerTest` verde sem edição (contratos HTTP inalterados).

## Pendências da Decisão 10 cobertas

Item 1 pelo cenário 10 + subida contra `payment_db` existente; 2–3 pelos testes de integração
(cenário 10); 4 pelo cenário 3; 5 pelos cenários 1, 2, 4–8; 6 pelo cenário 9.
