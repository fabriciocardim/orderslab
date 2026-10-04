# Quickstart: validar o invoice-api como consumidor e produtor Kafka

Validação empírica contra Postgres e Kafka reais (FR-014). Contratos em
[contracts/invoice-events.md](./contracts/invoice-events.md); esquema em [data-model.md](./data-model.md).

## Pré-requisitos

- Docker em execução; `docker compose -f infra/docker-compose.yml up -d postgres-api kafka`.
- Três serviços (um terminal cada): `order-api` na 8081, `payment-api` na 8082, `invoice-api` na
  8083 — `cd <serviço> && SERVER_PORT=<porta> ./mvnw spring-boot:run`.
- Consumir um tópico: `docker exec kafka-orderslab /opt/kafka/bin/kafka-console-consumer.sh
  --bootstrap-server localhost:9092 --topic <t> --from-beginning --timeout-ms 10000
  --formatter-property print.key=true --formatter-property print.headers=true`
- Banco: `docker exec postgres-api psql -U invoice_user -d invoice_db -c "<sql>"`
- **zsh**: ao iterar sobre listas de ids, use `for id in $(cat arquivo)` com um id por linha ou
  arrays; `for id in $VAR` não separa palavras no zsh.

## Cenário 1 — Nota emitida (US1, SC-001/SC-002)

`POST :8081/api/orders` `{"customerId":"c1","amount":10.50}`. **Esperado**: em até 15 s, um
`InvoiceIssued` em `invoice.issued` (chave = `orderId`, envelope completo, `invoiceId`/`paymentId`/
`amount`, sem `__TypeId__`); `GET :8083/api/invoices` mostra uma nota `ISSUED` do `orderId`.

## Cenário 2 — Nota que falha (US1)

Pedido de `amount` **750** (dentro do limite de pagamento, acima do de emissão). **Esperado**:
`PaymentReserved` em `payment.reserved`, depois um `InvoiceFailed`
(`reason":"AMOUNT_ABOVE_ISSUANCE_LIMIT"`) em `invoice.failed` e uma nota `FAILED`. Exatamente
`500` → `InvoiceIssued`.

## Cenário 3 — Pagamento que falhou não gera nota (US1.5)

Pedido de `amount` **1500**. **Esperado**: `PaymentFailed` em `payment.failed`; **nenhuma** nota nem
evento de nota para o `orderId`.

## Cenário 4 — Tópicos (Decisão 6.3)

`kafka-topics.sh --describe`: `payment.reserved`, `invoice.issued`, `invoice.failed` com
`PartitionCount: 3`, `ReplicationFactor: 1`.

## Cenário 5 — Reentrega não duplica (US2.1/2.2)

Reenviar o mesmo `PaymentReserved` (copiado do tópico) 2× com `kafka-console-producer.sh
--property parse.key=true --property key.separator='#'` e um terceiro com `eventId` novo e o mesmo
`orderId`. **Esperado**: logs `Duplicate PaymentReserved ignored`; continua 1 nota e 1 evento.

## Cenário 6 — Ilegíveis (FR-011)

Injetar em `payment.reserved`: `lixo-nao-json`, JSON sem `paymentId`, `amount` 0, seguido de um
`PaymentReserved` válido. **Esperado**: logs `ERROR` de descarte; o válido é processado; serviço `UP`.

## Cenário 7 — Broker parado (US2.3)

`docker stop kafka-orderslab`; criar pedidos; `docker start kafka-orderslab`. **Esperado**: os
outboxes dos três serviços esvaziam e todos os resultados chegam; `invoice-api` `UP` o tempo todo.

## Cenário 8 — Queda entre persistir e publicar (US2.4)

Subir o `invoice-api` com `OUTBOX_RELAY_INTERVAL_MS=3600000`; criar pedido; ver a nota decidida e a
linha em `outbox_events` com **0** no tópico; `kill -9`; reiniciar normalmente. **Esperado**: evento
publicado, 1 nota.

## Cenário 9 — Banco parado (US2.5)

`docker stop postgres-api`; injetar 3 `PaymentReserved` em `payment.reserved`; `docker start
postgres-api`. **Esperado**: as 3 viram notas, nenhuma descartada, outbox vazio.

## Cenário 10 — Ordem e observabilidade (US3, SC-006/SC-007)

Vários pedidos em sequência; por `orderId`, eventos na ordem das decisões. Nos logs ECS achar
recebimento/decisão (mesmo `traceId`) e envio (`originTraceId`).

## Cenário 11 — Suíte e contrato HTTP (FR-015, SC-008)

`cd invoice-api && ./mvnw clean verify` → `BUILD SUCCESS`, PMD limpo, `InvoiceControllerTest`
verde sem edição.
