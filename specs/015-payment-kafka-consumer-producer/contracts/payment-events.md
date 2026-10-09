# Contrato: eventos consumidos e publicados pelo payment-api

Instancia, para o `payment-api`, o contrato geral do E2.1
([event-contract.md](../../013-kafka-event-convention/contracts/event-contract.md)) e consome o
que o E2.2 publica ([order-events.md](../../014-order-kafka-producer/contracts/order-events.md)).
Nada aqui reabre o E2.1 nem o E2.2.

## Consumo

| Item | Valor |
|---|---|
| Tópico | `order.created` |
| Grupo de consumidores | `payment-api` |
| Posição inicial (grupo novo) | `earliest` |
| Chave / valor | texto UTF-8 / JSON (sem header de tipo) |
| Entrega | pelo menos uma vez; deduplicação pelo `eventId` |

Campos lidos de `OrderCreated` (os demais são ignorados — compatibilidade futura):

| Campo | Obrigatório | Uso |
|---|---|---|
| `eventId` | sim (UUID) | deduplicação |
| `orderId` | sim (UUID) | correlação; chave e `orderId` dos eventos de saída |
| `amount` | sim (decimal > 0) | decisão do pagamento |
| `eventType` | não | conferência (`"OrderCreated"`) quando presente |

Mensagem sem `eventId`/`orderId`/`amount` válidos, ou com JSON ilegível: **descartada** com log
`ERROR` (topic, partition, offset, trecho do valor); não bloqueia as seguintes.

## Publicação

| Evento | Tópico | Chave Kafka | Partições / réplicas |
|---|---|---|---|
| `PaymentReserved` | `payment.reserved` | `orderId` (string UUID) | 3 / 1 |
| `PaymentFailed` | `payment.failed` | `orderId` | 3 / 1 |

- Valor: JSON UTF-8, **sem header `__TypeId__`** (nem qualquer header de tipo Java).
- Entrega **pelo menos uma vez**; reentregas carregam o **mesmo `eventId`**.
- Ordem garantida **por pedido** (mesma chave → mesma partição), não entre pedidos.
- Como `PaymentReserved` e `PaymentFailed` estão em tópicos distintos, a ordem entre eles para
  o mesmo pedido é a de publicação do relay; consumidores que assinam mais de um tópico devem
  usar `occurredAt`.

## Payloads

### `PaymentReserved` — `payment.reserved`

```json
{
  "eventId": "9a7c1d2e-3b4f-4a5b-8c6d-7e8f9a0b1c2d",
  "eventType": "PaymentReserved",
  "eventVersion": 1,
  "occurredAt": "2026-10-04T10:15:31.004512Z",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "paymentId": "0b6f2c1a-8d3e-4f5a-9b7c-1d2e3f4a5b6c",
  "amount": 10.50
}
```

### `PaymentFailed` — `payment.failed`

```json
{
  "eventId": "…",
  "eventType": "PaymentFailed",
  "eventVersion": 1,
  "occurredAt": "…",
  "orderId": "…",
  "reason": "AMOUNT_LIMIT_EXCEEDED"
}
```

## Tipos

| Campo | Tipo JSON | Observação |
|---|---|---|
| `eventId`, `orderId`, `paymentId` | string (UUID) | `orderId` é o do pedido de origem |
| `eventType` | string | nome exato do E2.1 |
| `eventVersion` | number (int) | `1` |
| `occurredAt` | string ISO-8601 UTC | instante da decisão |
| `amount` | number (decimal) | só `PaymentReserved` |
| `reason` | string | só `PaymentFailed`; código estável |

Códigos de `reason` definidos: `AMOUNT_LIMIT_EXCEEDED` (valor estritamente maior que o limite de
aprovação, padrão `1000.00`). Novos códigos podem ser acrescentados sem mudar `eventVersion`.

## Compatibilidade

Acrescentar campos opcionais não exige novo `eventVersion`; remover/renomear campos ou mudar
tipo exige `eventVersion = 2`. Consumidores (o `invoice-api`, E2.4) devem ignorar campos
desconhecidos e deduplicar por `eventId`.
