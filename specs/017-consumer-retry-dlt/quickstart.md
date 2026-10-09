# Quickstart: validar retry limitado e DLT

Validação empírica contra Postgres e Kafka reais (FR-013). Contratos em
[contracts/dlt-contract.md](./contracts/dlt-contract.md).

## Pré-requisitos

- Docker; `docker compose -f infra/docker-compose.yml up -d postgres-api kafka`.
- `order-api` (8081), `payment-api` (8082) e `invoice-api` (8083):
  `cd <serviço> && SERVER_PORT=<porta> ./mvnw spring-boot:run`.
- Ver um tópico (com headers):
  `docker exec kafka-orderslab /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092
  --topic <t> --from-beginning --timeout-ms 10000 --formatter-property print.key=true
  --formatter-property print.headers=true`
- Injetar mensagens: `kafka-console-producer.sh --topic <t> --property parse.key=true
  --property key.separator='#'` (linhas `chave#valor`).
- **zsh**: iterar ids com `for id in $(cat arquivo)` (um por linha); `for x in $VAR` não separa palavras.

## Cenário 1 — Tópicos de DLT (FR-005)

`kafka-topics.sh --describe`: `order.created.dlt` e `payment.reserved.dlt` com `PartitionCount: 3`,
`ReplicationFactor: 1`.

## Cenário 2 — Mensagem ilegível vai ao DLT sem retry (US2)

Injetar em `order.created`: `lixo-nao-json`, JSON sem `orderId`, `amount` 0, e depois um `OrderCreated`
válido; idem em `payment.reserved` para o `invoice-api`. **Esperado**: as inválidas aparecem no DLT
(`order.created.dlt`/`payment.reserved.dlt`) com **chave e valor idênticos** e headers `kafka_dlt-*`;
logs mostram o envio ao DLT **sem** logs de retry; a válida é processada; serviço `UP`.

## Cenário 3 — Falha transitória prolongada → DLT sem perda (US1, SC-001/SC-002)

`docker stop postgres-api`; injetar 3 `OrderCreated` válidos; medir o tempo até aparecerem no DLT.
**Esperado**: logs de tentativas 1..5 com esperas crescentes (1, 2, 4, 8 s) e `connectionTimeout`
curto; cada mensagem no DLT em ≈ 40 s; nenhuma perdida. `docker start postgres-api`: o consumo retoma e
uma mensagem **nova** é processada normalmente.

## Cenário 4 — Falha que se resolve dentro do limite (US1.1)

Com o Postgres parado, injetar 1 mensagem e religar o banco em ~10 s. **Esperado**: processada na
tentativa seguinte, **nada** no DLT.

## Cenário 5 — DLT indisponível (US1.4/FR-007)

Provocar uma falha de conteúdo com o Kafka fora do ar não é possível (a mensagem não chega); em vez
disso o teste automatizado cobre o recoverer falhando; empiricamente, validar que **parar o Kafka
durante o ciclo de retry** de uma mensagem (via banco parado) não perde a mensagem: ao religar, ela vai
ao DLT ou é processada. Registrar o que se observar.

## Cenário 6 — Reprocessar do DLT (US3, SC-005)

Com mensagens válidas no DLT do cenário 3 e a causa corrigida: `kafka-console-consumer.sh` do DLT
(`--property print.key=true`, só chave e valor) e reenviar cada `chave#valor` ao tópico de origem.
**Esperado**: cada uma vira **um** pagamento/nota e um evento de saída; reenviar uma que já havia sido
processada → log `Duplicate ... ignored`, 0 duplicatas. Reenviar uma ainda inválida → volta ao DLT.

## Cenário 7 — Logs e rastreio (SC-006)

Nos logs ECS achar, para um pedido: cada retry (tentativa, causa, `topic/partition/offset`, `traceId`) e
o envio ao DLT (`orderId`/`eventId` quando legíveis).

## Cenário 8 — Suíte e contratos intactos (FR-014, SC-007)

`cd payment-api && ./mvnw clean verify` e `cd invoice-api && ./mvnw clean verify` → `BUILD SUCCESS`,
PMD limpo, testes de contrato HTTP e dos eventos de saída verdes sem edição.
