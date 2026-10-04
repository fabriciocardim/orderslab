# Quickstart: validar o order-api como produtor Kafka

Guia de validação empírica contra Postgres e Kafka reais (FR-013). Contratos em
[contracts/order-events.md](./contracts/order-events.md); esquema em
[data-model.md](./data-model.md).

## Pré-requisitos

- Docker em execução.
- Infra: `docker compose -f infra/docker-compose.yml up -d postgres-api kafka`
- Serviço: `cd order-api && ./mvnw spring-boot:run` (porta e credenciais padrão do
  `application.properties`; Kafka em `localhost:9092`).

Atalho para consumir: `docker exec kafka-orderslab /opt/kafka/bin/kafka-console-consumer.sh
--bootstrap-server localhost:9092 --topic <tópico> --from-beginning --property print.key=true
--property print.headers=true`

## Cenário 1 — Fluxo feliz (US1, SC-001, SC-005)

1. `POST /api/orders` `{"customerId":"c1","amount":10}`; anotar o `id`.
2. `POST /api/orders/{id}/confirm`; num segundo pedido, `POST /api/orders/{id}/cancel`.
3. Consumir `order.created`, `order.confirmed`, `order.cancelled`.

**Esperado**: 1 evento por operação, em até 5 s, com os 5 campos do envelope, `eventType`
exato, `eventVersion=1`, chave = `orderId`, **nenhum** header `__TypeId__`.

## Cenário 2 — Tópicos e partições (Decisão 7)

`kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic order.created`
(idem para os outros). **Esperado**: `PartitionCount: 3`, `ReplicationFactor: 1`.

## Cenário 3 — Rejeições não geram evento (US2.3, SC-002)

`GET/confirm` em UUID inexistente (404), `confirm` em pedido já cancelado (409), `POST` com
corpo inválido (400). **Esperado**: respostas inalteradas; nenhum evento novo; `outbox_events`
vazia.

## Cenário 4 — Broker fora do ar (US2.1–2.2, SC-003)

1. `docker stop kafka-orderslab`.
2. Criar 3 pedidos e confirmar 1. **Esperado**: todas as respostas 2xx normais; logs do relay
   com falha (WARN/ERROR) contendo `orderId`/`eventId`/`eventType`; `outbox_events` com as
   linhas pendentes.
3. `docker start kafka-orderslab`. **Esperado**: em segundos, os eventos chegam, na ordem por
   pedido, e `outbox_events` esvazia.

## Cenário 5 — Queda entre persistir e publicar (US2.4)

Com o broker parado, criar um pedido; `kill -9` no processo do `order-api`; religar o broker
e o serviço. **Esperado**: o evento pendente é publicado após o restart.

## Cenário 6 — Concorrência confirm × cancel (US2.5, SC-006)

Disparar `confirm` e `cancel` simultâneos no mesmo pedido PENDING (ex.: dois `curl &`).
**Esperado**: um 200 e um 409; exatamente 1 evento (`OrderConfirmed` **ou** `OrderCancelled`).

## Cenário 7 — Ordem e reentrega (US3, SC-004)

Criar e confirmar vários pedidos em sequência; por `orderId`, `OrderCreated` precede
`OrderConfirmed`/`OrderCancelled`. Reentrega (cenário 4/5 com falha após ack) mantém o mesmo
`eventId`.

## Cenário 8 — Observabilidade (SC-007)

Nos logs ECS do serviço, localizar por `orderId` o log de enqueue (com `traceId`) e o de envio
(com partição/offset e `originTraceId`).

## Cenário 9 — Suíte automatizada (FR-014)

`cd order-api && ./mvnw clean verify` com só o Docker ligado (sem Compose do projeto).
**Esperado**: `BUILD SUCCESS`, incluindo PMD; contratos HTTP inalterados (SC-008).

## Pendências da Decisão 10 cobertas

Itens 1–4 pelos testes de integração (cenário 9); itens 5–6 pelos cenários 1, 2, 4 e 5.
