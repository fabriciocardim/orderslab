# Data Model: Testes de Mensageria com Kafka Real

**Sem mudança de esquema, de entidade ou de código de produção.** Esta feature só acrescenta código de teste.

## Estrutura dos testes

| Elemento | Papel |
|---|---|
| Contêineres (`static`, por classe) | `PostgreSQLContainer("postgres:15-alpine")` e `KafkaContainer("apache/kafka:4.2.0")`, ambos `@ServiceConnection`; sobem uma vez por classe de teste. |
| `KafkaTestSupport` (um por serviço) | `consumerFor(topic)`, `awaitRecords(topic, key, count, timeout)`, `assertNoMore(topic, key, quiet)`, `headerNames(record)`. Polling com prazo; falha com mensagem descritiva. |
| Identificadores de teste | cada teste cria seus `orderId`/`eventId` (UUID) e filtra os registros pela **chave** = `orderId`; sem estado compartilhado entre testes. |
| Parâmetros de teste | `outbox.relay.interval-ms=200`; nos consumidores, `consumer.retry.initial-interval-ms=3000` (lento de propósito, para provar "DLT sem retry"). |

## Mensagens de teste

| Serviço | Publicado pelo teste | Observado pelo teste |
|---|---|---|
| order-api | requisições REST (`POST /api/orders`, `/confirm`, `/cancel`) | `order.created`, `order.confirmed`, `order.cancelled` |
| payment-api | `OrderCreated` (JSON do contrato) e textos inválidos em `order.created` | `payment.reserved`, `payment.failed`, `order.created.dlt`; tabela `payments` por JDBC |
| invoice-api | `PaymentReserved` (JSON do contrato) e textos inválidos em `payment.reserved` | `invoice.issued`, `invoice.failed`, `payment.reserved.dlt`; tabela `invoices` por JDBC |
