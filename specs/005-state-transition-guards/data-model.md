# Data Model: Máquina de Estados / Guardas de Transição

## Enums revisados

### `OrderStatus` (order-api)

| Valor | Antes | Depois |
|---|---|---|
| `PENDING` | mantido | mantido — estado inicial |
| `CONFIRMED` | mantido | mantido — terminal |
| `CANCELLED` | mantido | mantido — terminal |
| `COMPLETED` | existia, nunca setado | **removido** |

### `PaymentStatus` (payment-api)

| Valor | Antes | Depois |
|---|---|---|
| `PENDING` | setado no construtor, nunca observável | **removido** |
| `RESERVED` | setado por `reserve()` | mantido — vira o estado inicial (setado direto no construtor) |
| `CONFIRMED` | mantido | mantido — terminal |
| `CANCELLED` | mantido | mantido — terminal |
| `FAILED` | existia, nunca setado | **removido** |

### `InvoiceStatus` (invoice-api)

| Valor | Antes | Depois |
|---|---|---|
| `PENDING` | mantido | mantido — estado inicial |
| `ISSUED` | mantido | mantido — terminal |
| `CANCELLED` | mantido | mantido — terminal |
| `FAILED` | existia, nunca setado | **removido** |

## Transições válidas (por serviço)

| Serviço | Estado inicial | Ação | Estado final | Guarda |
|---|---|---|---|---|
| order-api | `PENDING` | `confirm()` | `CONFIRMED` | só a partir de `PENDING` |
| order-api | `PENDING` | `cancel()` | `CANCELLED` | só a partir de `PENDING` |
| payment-api | `RESERVED` | `confirm()` | `CONFIRMED` | só a partir de `RESERVED` |
| payment-api | `RESERVED` | `cancel()` | `CANCELLED` | só a partir de `RESERVED` |
| invoice-api | `PENDING` | `issue()` | `ISSUED` | só a partir de `PENDING` |
| invoice-api | `PENDING` | `cancel()` | `CANCELLED` | só a partir de `PENDING` |

`CONFIRMED`/`CANCELLED`/`ISSUED` são estados terminais — nenhuma transição sai deles.

## `InvalidStatusTransitionException` (nova, por serviço)

DTO/exceção interna, não um objeto de resposta — traduzida para `ErrorResponse` (já
existente) pelo `GlobalExceptionHandler`.

| Campo | Tipo | Descrição |
|---|---|---|
| status atual | enum do serviço (`OrderStatus`/`PaymentStatus`/`InvoiceStatus`) | estado em que o recurso estava quando a transição foi rejeitada |
| ação tentada | `String` | nome da ação rejeitada (ex.: `"confirmar"`, `"cancelar"`, `"emitir"`) |

Mensagem resultante (exemplo): `"Pedido já está CANCELLED, não pode ser confirmado."`
