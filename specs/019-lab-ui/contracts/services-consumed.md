# Contrato: o que a UI consome dos serviços (somente leitura/uso das rotas existentes)

A UI **não altera** nenhum serviço; usa apenas as rotas abaixo, todas pela própria origem (proxy do nginx / Vite).

| Prefixo na UI | Serviço | Método e rota | Uso |
|---|---|---|---|
| `/api/orders` | order-api | `POST /api/orders` `{customerId, amount}` → **201** `Order` | criar pedido |
| | | `GET /api/orders` → `Order[]` | listagem |
| | | `GET /api/orders/{id}` → `Order` / **404** | acompanhar |
| | | `POST /api/orders/{id}/confirm` → `Order` / **404** / **409** | confirmar |
| | | `POST /api/orders/{id}/cancel` → `Order` / **404** / **409** | cancelar |
| `/api/payments` | payment-api | `GET /api/payments` → `Payment[]` | listagem e localizar por `orderId` (filtro no cliente) |
| `/api/invoices` | invoice-api | `GET /api/invoices` → `Invoice[]` | listagem e localizar por `orderId` (filtro no cliente) |
| `/health/order` `/health/payment` `/health/invoice` | cada serviço | `GET /actuator/health` → **200** `{status:"UP"}` / **503** `{status:"DOWN"}` | saúde |

## Erros e como a UI os trata

| Resposta | Tratamento |
|---|---|
| **400** `ErrorResponse` com `validationErrors` (`campo: mensagem`) | mensagens por campo no formulário (cliente, valor) |
| **404** | "Pedido não encontrado" ao acompanhar/agir |
| **409** | mensagem de "estado inválido" ao confirmar/cancelar (ex.: pedido já cancelado) |
| 5xx / 502 / 504 / timeout (3 s) / rede | painel/lista do serviço = "indisponível" (mantém último dado conhecido) |
| corpo inesperado | trata como indisponível/inesperado naquele painel, sem quebrar a tela |

## Observações

- `payment-api` e `invoice-api` **não** têm filtro por pedido nem paginação: a UI baixa a lista e filtra/ordena no cliente.
- O motivo detalhado da falha (`AMOUNT_...`) **não** é exposto pelo REST; só está no evento (Kafbat).
- Nenhum header especial, autenticação ou CORS é necessário (mesma origem).
