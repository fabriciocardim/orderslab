# Data Model: Persistência JPA Simétrica

Mapeamento das classes `Model` existentes para tabelas reais. Campos/tipos já existem hoje
em cada classe — esta feature só adiciona anotações JPA e a migração correspondente, sem
mudar nomes ou tipos de campo em Java.

## `orders` (order-api → banco `order_db`)

| Coluna | Tipo Postgres | Campo Java | Anotação |
|---|---|---|---|
| `id` | `uuid` (PK) | `UUID id` | `@Id` |
| `customer_id` | `varchar(255)` | `String customerId` | — |
| `amount` | `numeric(19,2)` | `BigDecimal amount` | — |
| `status` | `varchar(20)` | `OrderStatus status` | `@Enumerated(EnumType.STRING)` |
| `created_at` | `timestamptz` | `Instant createdAt` | — |
| `updated_at` | `timestamptz` | `Instant updatedAt` | — |

Valores possíveis de `status` (item 1.5): `PENDING`, `CONFIRMED`, `CANCELLED`.

## `payments` (payment-api → banco `payment_db`)

| Coluna | Tipo Postgres | Campo Java | Anotação |
|---|---|---|---|
| `id` | `uuid` (PK) | `UUID id` | `@Id` |
| `order_id` | `varchar(255)` | `String orderId` | — |
| `amount` | `numeric(19,2)` | `BigDecimal amount` | — |
| `status` | `varchar(20)` | `PaymentStatus status` | `@Enumerated(EnumType.STRING)` |
| `created_at` | `timestamptz` | `Instant createdAt` | — |
| `updated_at` | `timestamptz` | `Instant updatedAt` | — |

Valores possíveis de `status` (item 1.5): `RESERVED`, `CONFIRMED`, `CANCELLED`.

## `invoices` (invoice-api → banco `invoice_db`)

| Coluna | Tipo Postgres | Campo Java | Anotação |
|---|---|---|---|
| `id` | `uuid` (PK) | `UUID id` | `@Id` |
| `order_id` | `varchar(255)` | `String orderId` | — |
| `payment_id` | `varchar(255)` | `String paymentId` | — |
| `amount` | `numeric(19,2)` | `BigDecimal amount` | — |
| `status` | `varchar(20)` | `InvoiceStatus status` | `@Enumerated(EnumType.STRING)` |
| `created_at` | `timestamptz` | `Instant createdAt` | — |
| `updated_at` | `timestamptz` | `Instant updatedAt` | — |

Valores possíveis de `status` (item 1.5): `PENDING`, `ISSUED`, `CANCELLED`.

## Notas

- `order_id`/`payment_id` continuam `varchar`, não `uuid` — decisão já registrada no item
  1.4 (validados por formato via `@Pattern`, sem migrar o tipo do campo; ver
  `specs/004-request-validation/research.md`). Migrar para uma foreign key de verdade exigiria
  referência cross-database, que o Postgres não suporta nativamente entre bancos separados
  (decisão do item 1.9) — fora de escopo aqui.
- Sem relacionamento JPA (`@ManyToOne`/`@OneToOne`) entre as 3 entidades — cada serviço só
  enxerga sua própria tabela, no seu próprio banco; a referência a outro serviço é só um
  identificador de texto, validado por formato mas não por integridade referencial (consistente
  com o Princípio I — bancos separados, sem acesso cruzado).
- Nenhuma transição de estado nova — as regras já implementadas no item 1.5 (`Service`)
  continuam sendo a única fonte de verdade sobre quais transições são válidas; o banco só
  armazena o estado atual, não valida transições.
