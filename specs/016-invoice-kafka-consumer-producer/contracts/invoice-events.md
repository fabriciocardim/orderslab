# Contrato: eventos consumidos e publicados pelo invoice-api

Instancia, para o `invoice-api`, o contrato geral do E2.1
([event-contract.md](../../013-kafka-event-convention/contracts/event-contract.md)) e consome o que
o E2.3 publica ([payment-events.md](../../015-payment-kafka-consumer-producer/contracts/payment-events.md)).

## Consumo

| Item | Valor |
|---|---|
| Tópico | `payment.reserved` (`payment.failed` **não** é assinado) |
| Grupo | `invoice-api` |
| Posição inicial (grupo novo) | `earliest` |
| Chave / valor | texto UTF-8 / JSON (sem header de tipo) |
| Entrega | pelo menos uma vez; dedup por `eventId` |

Campos lidos de `PaymentReserved` (demais ignorados): `eventId` (UUID, obrigatório), `orderId`
(UUID, obrigatório), `paymentId` (UUID, obrigatório), `amount` (decimal > 0, obrigatório). Mensagem
ilegível, incompleta ou com `amount <= 0`: descartada com log `ERROR` (topic, partition, offset,
trecho do valor); não bloqueia as seguintes.

## Publicação

| Evento | Tópico | Chave Kafka | Partições / réplicas |
|---|---|---|---|
| `InvoiceIssued` | `invoice.issued` | `orderId` | 3 / 1 |
| `InvoiceFailed` | `invoice.failed` | `orderId` | 3 / 1 |

Valor: JSON UTF-8, **sem header `__TypeId__`**; entrega pelo menos uma vez com `eventId` estável;
ordem por pedido (mesma chave → mesma partição).

### `InvoiceIssued` — `invoice.issued`

```json
{
  "eventId": "5d1e8a3c-2b7f-4c9a-8e10-6f2a3b4c5d6e",
  "eventType": "InvoiceIssued",
  "eventVersion": 1,
  "occurredAt": "2026-10-04T10:15:32.118004Z",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "invoiceId": "7c2b9d4e-1a3f-4b5c-9d6e-0f1a2b3c4d5e",
  "paymentId": "0b6f2c1a-8d3e-4f5a-9b7c-1d2e3f4a5b6c",
  "amount": 10.50
}
```

### `InvoiceFailed` — `invoice.failed`

```json
{
  "eventId": "…", "eventType": "InvoiceFailed", "eventVersion": 1, "occurredAt": "…",
  "orderId": "…", "paymentId": "…", "reason": "AMOUNT_ABOVE_ISSUANCE_LIMIT"
}
```

| Campo | Tipo JSON | Observação |
|---|---|---|
| `eventId`, `orderId`, `invoiceId`, `paymentId` | string (UUID) | `orderId` é o do pedido de origem |
| `eventType` | string | nome exato do E2.1 |
| `eventVersion` | number (int) | `1` |
| `occurredAt` | string ISO-8601 UTC | instante da decisão |
| `amount` | number | só `InvoiceIssued` |
| `reason` | string | só `InvoiceFailed`; código estável |

`reason` definido: `AMOUNT_ABOVE_ISSUANCE_LIMIT` (valor estritamente maior que o limite de emissão,
padrão `500.00`). Novos códigos não exigem novo `eventVersion`. Consumidores devem ignorar campos
desconhecidos e deduplicar por `eventId`.
