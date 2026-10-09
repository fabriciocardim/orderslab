# Data Model: Validação de Request (Bean Validation)

## Campos validados (por DTO)

### `OrderRequest` (order-api)

| Campo | Tipo | Regra |
|---|---|---|
| `customerId` | `String` | `@NotBlank` — MUST estar presente e não-vazio/não-branco |
| `amount` | `BigDecimal` | `@NotNull @Positive` — MUST estar presente e MUST ser > 0 |

### `PaymentReservationRequest` (payment-api)

| Campo | Tipo | Regra |
|---|---|---|
| `orderId` | `String` | `@NotBlank @Pattern` — MUST estar presente, não-vazio, e MUST casar com o formato UUID (`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`) |
| `amount` | `BigDecimal` | `@NotNull @Positive` |

### `InvoiceRequest` (invoice-api)

| Campo | Tipo | Regra |
|---|---|---|
| `orderId` | `String` | `@NotBlank @Pattern` — mesma regra de `PaymentReservationRequest.orderId` |
| `paymentId` | `String` | `@NotBlank @Pattern` — mesma regra, aplicada a `paymentId` |
| `amount` | `BigDecimal` | `@NotNull @Positive` |

## `ErrorResponse` (estendido — ver research.md, Decisão 4)

| Campo | Tipo | Descrição | Regra |
|---|---|---|---|
| `timestamp` | `Instant` | (já existia, item 1.3) | inalterado |
| `status` | `int` | (já existia) | `400` para erros de validação; `404` inalterado |
| `error` | `String` | (já existia) | `"Bad Request"` para validação; `"Not Found"` inalterado |
| `message` | `String` | (já existia) | mensagem geral (ex.: `"Erro de validação"`) para o caso 400 |
| `path` | `String` | (já existia) | inalterado |
| `validationErrors` | `List<String>` (novo) | lista de `"<campo>: <motivo>"`, um por campo inválido | `null` quando não há erro de validação (ex.: resposta 404); MUST conter 1 entrada por campo inválido quando presente |

Sem novas entidades persistidas — os DTOs de request continuam sendo objetos de transporte
(não persistidos), e `ErrorResponse` continua um DTO de resposta.
