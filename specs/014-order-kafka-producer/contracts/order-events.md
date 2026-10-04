# Contrato: eventos publicados pelo order-api

Instancia, para o `order-api`, o contrato geral do E2.1
([event-contract.md](../../013-kafka-event-convention/contracts/event-contract.md)). Nada aqui
reabre o E2.1.

## Tópicos e entrega

| Evento | Tópico | Chave Kafka | Partições / réplicas |
|---|---|---|---|
| `OrderCreated` | `order.created` | `orderId` (string UUID) | 3 / 1 |
| `OrderConfirmed` | `order.confirmed` | `orderId` | 3 / 1 |
| `OrderCancelled` | `order.cancelled` | `orderId` | 3 / 1 |

- Valor da mensagem: JSON UTF-8, **sem header `__TypeId__`** (nem qualquer header de tipo Java).
- Entrega **pelo menos uma vez**: reentregas carregam o **mesmo `eventId`**; o consumidor
  deduplica por ele.
- Ordem garantida **por pedido** (mesma chave → mesma partição), não entre pedidos.
- Como `OrderCreated`, `OrderConfirmed` e `OrderCancelled` estão em tópicos distintos, a ordem
  entre eles para o mesmo pedido é a ordem de publicação do relay; um consumidor que assina
  mais de um tópico deve tolerar chegada fora de ordem *entre tópicos* e usar `occurredAt`.

## Payloads

### `OrderCreated` — `order.created`

```json
{
  "eventId": "1f8b1c9e-6e2a-4f3b-9a11-4b0a6b0f2c34",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-10-04T10:15:30.123456Z",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerId": "cliente-1",
  "amount": 10.50
}
```

### `OrderConfirmed` — `order.confirmed` / `OrderCancelled` — `order.cancelled`

Somente o envelope:

```json
{
  "eventId": "…", "eventType": "OrderConfirmed", "eventVersion": 1,
  "occurredAt": "…", "orderId": "…"
}
```

## Tipos

| Campo | Tipo JSON | Observação |
|---|---|---|
| `eventId`, `orderId` | string (UUID) | |
| `eventType` | string | nome exato da classe/tabela do E2.1 |
| `eventVersion` | number (int) | `1` |
| `occurredAt` | string ISO-8601 UTC | instante da transição |
| `customerId` | string | só `OrderCreated` |
| `amount` | number (decimal) | só `OrderCreated`; escala conforme o pedido |

## Compatibilidade

Acrescentar campos opcionais não exige novo `eventVersion`; remover/renomear campos ou mudar
tipo exige `eventVersion = 2`. Consumidores devem ignorar campos desconhecidos.

## Esclarecimento ao E2.1 (serializador)

O E2.1 cita `JacksonJsonSerializer`. Aqui o payload já sai serializado do outbox (Jackson 3,
mesmo `JsonMapper` do Spring Boot) e é publicado como texto com `StringSerializer`. O formato
de fio é **idêntico** ao do `JacksonJsonSerializer` (verificado em `research.md`, Decisão 5),
sem o header `__TypeId__`. A família Jackson 3 e o JSON no fio são preservados.
