# Contrato: listagens filtradas e limitadas

Vale igual para `payment-api` (`/api/payments`) e `invoice-api` (`/api/invoices`); `order-api` (`/api/orders`) só tem a
parte de **limite** (o acompanhamento já usa `GET /api/orders/{id}`).

## `GET /api/payments?orderId={uuid}`

| Caso | Status | Corpo |
|---|---|---|
| Pedido com pagamento(s) | 200 | `[PaymentResponse, ...]`, `createdAt` desc |
| Pedido sem pagamento (ainda) | 200 | `[]` |
| `orderId` não é UUID | 400 | `ErrorResponse` com `validationErrors: ["orderId: valor inválido"]` |

Sem `X-Total-Count`. Se um `limit` válido também vier, é ignorado (um `limit` inválido continua dando 400: a validação
de parâmetros acontece antes do método).

## `GET /api/payments?limit={n}` (e sem parâmetros)

| Caso | Status | Corpo | Cabeçalho |
|---|---|---|---|
| `limit` omitido | 200 | até 200 registros, `createdAt` desc | `X-Total-Count: <total>` |
| `1 <= limit <= 500` | 200 | até `limit` registros, `createdAt` desc | `X-Total-Count: <total>` |
| `limit < 1` ou `> 500` | 400 | `ErrorResponse` com `validationErrors: ["limit: ..."]` | — |
| `limit` não numérico | 400 | `ErrorResponse` com `validationErrors: ["limit: valor inválido"]` | — |

## Formato dos registros

Inalterado (`PaymentResponse`, `InvoiceResponse`, `OrderResponse`).

## Erro (`ErrorResponse`, inalterado)

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Erro de validação",
  "path": "/api/payments", "validationErrors": ["orderId: valor inválido"] }
```
