# Contrato: Resposta de erro 404 (recurso não encontrado)

Aplica-se aos 3 serviços, idêntico em formato, implementado de forma independente em cada
um (ver `research.md`, Decisão 2).

## Quando ocorre

Qualquer endpoint que busque um recurso por id e não o encontre:

| Serviço | Endpoints afetados |
|---|---|
| `order-api` | `GET /api/orders/{id}`, `POST /api/orders/{id}/confirm`, `POST /api/orders/{id}/cancel` |
| `payment-api` | `GET /api/payments/{id}`, `POST /api/payments/{id}/confirm`, `POST /api/payments/{id}/cancel` |
| `invoice-api` | `GET /api/invoices/{id}`, `POST /api/invoices/{id}/issue`, `POST /api/invoices/{id}/cancel` |

## Resposta

**Status**: `404 Not Found`

**Content-Type**: `application/json`

**Body**:

```json
{
  "timestamp": "2026-09-24T15:30:00.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Pedido não encontrado: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "path": "/api/orders/3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

`message` varia por serviço (texto da respectiva `*NotFoundException`); os outros 4 campos
seguem exatamente o mesmo nome e tipo nos 3 serviços.

## Não afetado por este contrato

- Sucesso (`200`/`201`) em qualquer endpoint — sem mudança.
- `400 Bad Request` para path variable mal formada (ex.: `id` que não é um UUID válido) —
  já é o comportamento padrão do Spring, antes de chegar ao handler desta feature.
