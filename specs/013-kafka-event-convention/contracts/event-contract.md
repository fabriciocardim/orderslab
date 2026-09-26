# Contrato: Eventos e Tópicos Kafka

Este é o contrato de referência que os itens E2.2 (`order-api` produtor), E2.3
(`payment-api` consumidor+produtor) e E2.4 (`invoice-api` consumidor+produtor) MUST seguir.
Nenhum código de produtor/consumidor é implementado por esta feature — só o contrato.

## Mecanismo de serialização

`org.springframework.kafka.support.serializer.JacksonJsonSerializer` (produtor) /
`JacksonJsonDeserializer` (consumidor) — família Jackson 3, consistente com o resto do
projeto desde o item 1.7. **Nunca** as classes clássicas `JsonSerializer`/`JsonDeserializer`
(Jackson 2). Ver `research.md` Decisão 1.

## Convenção de nome de tópico

`<domínio>.<evento>`, tudo minúsculas, um tópico por tipo de evento:

| Evento (nome de classe Java) | Serviço produtor | Tópico |
|---|---|---|
| `OrderCreated` | order-api | `order.created` |
| `OrderConfirmed` | order-api | `order.confirmed` |
| `OrderCancelled` | order-api | `order.cancelled` |
| `PaymentReserved` | payment-api | `payment.reserved` |
| `PaymentFailed` | payment-api | `payment.failed` |
| `InvoiceIssued` | invoice-api | `invoice.issued` |
| `InvoiceFailed` | invoice-api | `invoice.failed` |

## Envelope obrigatório (todo evento)

Todo payload de evento MUST conter, no mínimo, estes campos — além dos campos específicos de
cada tipo de evento (decididos por quem o implementa, em E2.2-E2.4):

```json
{
  "eventId": "1f8b1c9e-6e2a-4f3b-9a11-4b0a6b0f2c34",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-09-26T10:15:30Z",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

| Campo | Tipo | Regra |
|---|---|---|
| `eventId` | UUID | Único por instância de evento — não é o id do recurso (pedido/pagamento/nota) |
| `eventType` | String | Nome exato da tabela acima (ex.: `"OrderCreated"`) |
| `eventVersion` | Integer | Começa em `1`; incrementar ao mudar o schema de um evento de forma incompatível |
| `occurredAt` | Instant (ISO-8601, UTC) | Momento em que a transição de estado ocorreu |
| `orderId` | String (formato UUID) | **Obrigatório em todo evento, mesmo os de `payment-api`/`invoice-api`** — é o identificador de correlação da transação de negócio |

## Regra de correlação

`orderId` é o único identificador de correlação. Um evento `PaymentReserved` ou
`InvoiceIssued` (que não são "sobre" um pedido diretamente) MUST carregar o `orderId` do
pedido que originou a cadeia de eventos — não apenas o id do próprio recurso
(pagamento/nota).

## O que fica para E2.2-E2.4

- Campos específicos de cada evento (ex.: `customerId`/`amount` em `OrderCreated`,
  `paymentId` em `PaymentReserved`).
- Implementação real do produtor/consumidor (`@KafkaListener`, configuração de
  `ProducerFactory`/`ConsumerFactory`, etc.).
- Nome da classe de evento Java em cada serviço (livre, desde que o campo `eventType` no
  payload serializado use exatamente o nome da tabela acima).

## O que fica para itens futuros (fora do escopo de E2.1-E2.4)

- Estratégia de retry/dead-letter (E2.5).
- Testes de mensageria com Testcontainers Kafka (E2.6).
- Schema Registry ou qualquer validação de compatibilidade automatizada entre versões de
  schema — `eventVersion` é só um campo informativo nesta etapa.
