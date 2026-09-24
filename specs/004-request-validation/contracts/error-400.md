# Contrato: Resposta de erro 400 (validação de request)

Estende o contrato de erro já estabelecido em
[`specs/003-http-404-error-handling/contracts/error-404.md`](../../003-http-404-error-handling/contracts/error-404.md)
— mesmo `ErrorResponse`, agora com o campo opcional `validationErrors` preenchido.

## Quando ocorre

| Serviço | Endpoint |
|---|---|
| `order-api` | `POST /api/orders` |
| `payment-api` | `POST /api/payments` |
| `invoice-api` | `POST /api/invoices` |

Quando o corpo da requisição falha a validação Bean Validation (ver `data-model.md`).

## Resposta

**Status**: `400 Bad Request`

**Content-Type**: `application/json`

**Body** (exemplo com 2 campos inválidos simultaneamente):

```json
{
  "timestamp": "2026-09-24T18:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Erro de validação",
  "path": "/api/orders",
  "validationErrors": [
    "customerId: não deve estar em branco",
    "amount: deve ser maior que zero"
  ]
}
```

`validationErrors` é `null`/ausente nas respostas `404` (contrato anterior, inalterado).

## Não afetado por este contrato

- Sucesso (`201`) com dados válidos — sem mudança.
- `404` (recurso não encontrado) — contrato do item 1.3 inalterado, apenas ganha um campo
  novo que permanece `null` nesse caso.
- `400` por JSON malformado (antes de chegar à validação Bean Validation) — já é o
  comportamento padrão do Spring; fora do escopo desta feature.
