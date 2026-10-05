# Data Model: UI do Laboratório

**A UI não persiste nada.** O "modelo" são os tipos lidos das APIs e o estado derivado exibido na tela.

## Tipos lidos das APIs (TypeScript, `src/domain/types.ts`)

| Tipo | Campos | Estados (`status`) |
|---|---|---|
| `Order` | `id` (UUID), `customerId`, `amount` (number), `status`, `createdAt`, `updatedAt` | `PENDING` \| `CONFIRMED` \| `CANCELLED` |
| `Payment` | `id`, `orderId` (string UUID), `amount`, `status`, `createdAt`, `updatedAt` | `RESERVED` \| `CONFIRMED` \| `CANCELLED` \| `FAILED` |
| `Invoice` | `id`, `orderId`, `paymentId`, `amount`, `status`, `createdAt`, `updatedAt` | `PENDING` \| `ISSUED` \| `CANCELLED` \| `FAILED` |
| `ApiError` | `status` (int), `message`, `validationErrors?: string[]`, `path?` | formato `ErrorResponse` dos serviços (400/404/409) |
| `Health` | `status` `UP` \| `DOWN` \| `UNKNOWN` | derivado de `/health/{svc}` |

`amount` chega como número JSON (BigDecimal). Datas em ISO-8601 UTC.

## Estado derivado (só em memória)

- **`StepState`** = `done` \| `failed` \| `waiting` \| `not_applicable` \| `cancelled`.
- **`Flow`** = `{ order: StepState, payment: StepState, invoice: StepState, final: boolean }`, produzido por
  `deriveFlow(order, payments, invoices)`. Regras (tabela completa em [research.md](./research.md), Decisão 4):
  - pedido existe → `done`;
  - pagamento: `RESERVED|CONFIRMED` → `done`; `FAILED` → `failed`; `CANCELLED` → `cancelled`; ausente e pedido existe →
    `waiting` (ou `not_applicable` se o pedido foi cancelado sem pagamento);
  - nota: `ISSUED` → `done`; `FAILED` → `failed`; `CANCELLED` → `cancelled`; `PENDING` → `waiting`; ausente e
    pagamento `done` → `waiting`; pagamento `failed` → `not_applicable`;
  - `final` = nenhuma etapa em `waiting`.
- **`TrackedOrder`** = `{ orderId, startedAt, lastFingerprint, lastChangeAt, stopped: boolean }` — controla o polling
  (parar em `final` ou após ~30 s sem mudança de *fingerprint*).
- **`PanelState<T>`** = `{ data?: T, lastKnown?: T, state: 'ok' | 'loading' | 'unavailable' }` por serviço/lista.

## Constantes de domínio da tela

| Constante | Valor | Origem |
|---|---|---|
| Cenários de valor | baixo `10.50` · exato `500` · entre `750` · exato `1000` · acima `1500` | limites: pagamento > 1000.00 falha; nota > 500.00 falha |
| Polling | 2000 ms | spec FR-006 |
| Parada sem mudança | ~30 s | spec FR-006 |
| Timeout por chamada | 3000 ms | spec FR-011 |
| Limite de linhas por lista | 200 mais recentes (por `createdAt` desc) | spec FR-008 |
| Tópicos (7) | `order.created` · `order.confirmed` · `order.cancelled` · `payment.reserved` · `payment.failed` · `invoice.issued` · `invoice.failed` | contrato do E2.1 |
| DLTs (2) | `order.created.dlt` · `payment.reserved.dlt` | convenção do E2.5 |

## Configuração em runtime (`config.js`)

`window.__LAB_CONFIG__ = { kafbatUrl, kafbatCluster }` com padrões `http://localhost:8090` / `orderslab`; a UI lê isso
em `src/config.ts` (com valores padrão se o arquivo não definir).
