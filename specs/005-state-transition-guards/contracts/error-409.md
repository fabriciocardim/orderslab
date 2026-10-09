# Contrato: Resposta de erro 409 (transição ilegal)

Reaproveita o `ErrorResponse` já estabelecido em
[`error-404.md`](../../003-http-404-error-handling/contracts/error-404.md) (item 1.3) e
estendido em [`error-400.md`](../../004-request-validation/contracts/error-400.md) (item
1.4) — mesmo formato, `validationErrors` sempre `null` neste caso.

## Quando ocorre

| Serviço | Endpoints afetados | Estado exigido |
|---|---|---|
| `order-api` | `POST /api/orders/{id}/confirm`, `POST /api/orders/{id}/cancel` | `PENDING` |
| `payment-api` | `POST /api/payments/{id}/confirm`, `POST /api/payments/{id}/cancel` | `RESERVED` |
| `invoice-api` | `POST /api/invoices/{id}/issue`, `POST /api/invoices/{id}/cancel` | `PENDING` |

Chamado quando o recurso existe (senão seria 404) mas não está no estado exigido.

## Resposta

**Status**: `409 Conflict`

**Content-Type**: `application/json`

**Body**:

```json
{
  "timestamp": "2026-09-24T19:00:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "Pedido já está CANCELLED, não pode ser confirmado.",
  "path": "/api/orders/3fa85f64-5717-4562-b3fc-2c963f66afa6/confirm",
  "validationErrors": null
}
```

## Não afetado por este contrato

- `POST` de criação (`/api/orders`, `/api/payments`, `/api/invoices`) — sem mudança.
- `GET` (`findAll`/`findById`) — sem mudança.
- Id inexistente — continua `404` (item 1.3), não `409`.
- Primeira transição válida a partir do estado inicial — continua `200`, sem mudança.
