# Research: Convenção de Evento/Tópico Kafka

## Decisão 1: `JacksonJsonSerializer`/`JacksonJsonDeserializer` (não `JsonSerializer`/`JsonDeserializer` clássicos)

**Decision**: quando E2.2-E2.4 implementarem produtor/consumidor real, MUST usar
`org.springframework.kafka.support.serializer.JacksonJsonSerializer`/`JacksonJsonDeserializer`
(mais `JacksonJavaTypeMapper`), não as classes clássicas `JsonSerializer`/`JsonDeserializer`.

**Rationale**: confirmado via inspeção de bytecode do jar `spring-kafka-4.1.1.jar` que
**ambas as famílias existem simultaneamente**: a clássica usa
`com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2, mantida só por compatibilidade); a
nova (`Jackson*`) usa `tools.jackson.databind.json.JsonMapper` (Jackson 3) — exatamente o
mesmo padrão de divisão Jackson 2/3 já encontrado no item 1.7 desta sessão (`@WebMvcTest` e
Jackson 3 no contexto de teste). Como o projeto já opera inteiramente em Jackson 3 (nenhuma
dependência Jackson 2 nos 3 serviços), usar a família clássica arrastaria Jackson 2 de volta
ao classpath só para serialização de evento — inconsistente e desnecessário. Nenhuma
dependência Maven nova é exigida: as duas famílias de classe já vêm dentro do próprio
`spring-kafka`, transitivamente presente via `spring-boot-starter-kafka` desde o item 1.1.

**Alternatives considered**: `JsonSerializer`/`JsonDeserializer` clássicos — rejeitados por
reintroduzirem Jackson 2 no classpath, indo contra a decisão já tomada (implicitamente) desde
o item 1.7; Avro/Protobuf com Schema Registry — rejeitado como excesso de complexidade para
o estágio atual do laboratório (Princípio II); nenhum Schema Registry está provisionado e
introduzi-lo seria uma decisão de infraestrutura maior do que o escopo deste item.

## Decisão 2: Convenção de nome de tópico — `<domínio>.<evento>` em minúsculas

**Decision**: um tópico por tipo de evento, nome no formato `dominio.evento` (minúsculas,
separado por ponto), onde `dominio` é o agregado de negócio (`order`/`payment`/`invoice`) e
`evento` é a ação em particípio (`created`/`confirmed`/`cancelled`/`reserved`/`issued`/
`failed`). Os 6 tópicos resultantes dos eventos já nomeados no ROADMAP:

| Evento (nome de classe) | Tópico |
|---|---|
| `OrderCreated` | `order.created` |
| `OrderConfirmed` | `order.confirmed` |
| `OrderCancelled` | `order.cancelled` |
| `PaymentReserved` | `payment.reserved` |
| `PaymentFailed` | `payment.failed` |
| `InvoiceIssued` | `invoice.issued` |
| `InvoiceFailed` | `invoice.failed` |

**Rationale**: `domínio.evento` é a convenção mais comum no ecossistema Kafka para
"um tópico por tipo de evento" (em oposição a "um tópico por agregado" com múltiplos tipos de
evento misturados) — mantém cada consumidor inscrito só no que precisa (ex.: `payment-api`
só assina `order.created`, não todos os eventos de `order-api`). Nome do tópico
deliberadamente distinto do nome da classe de evento (minúsculas/pontuação) para deixar claro
que são conceitos diferentes (nome de infraestrutura vs. nome de tipo Java).

**Alternative considered**: um único tópico por agregado (ex.: `orders`) com o tipo de evento
como campo do payload — rejeitado porque obrigaria todo consumidor a filtrar eventos que não
lhe interessam, e a spec já assume "um tópico por tipo de evento" implicitamente ao nomear 6
eventos distintos.

## Decisão 3: Envelope comum obrigatório em todo evento

**Decision**: todo payload de evento MUST conter, no mínimo, estes campos de envelope, além
dos campos específicos de cada evento:

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `eventId` | UUID | Sim | Identificador único desta instância de evento (não do recurso) |
| `eventType` | String | Sim | Nome exato do evento (ex.: `"OrderCreated"`) — mesmo nome usado no ROADMAP |
| `eventVersion` | Integer | Sim | Versão do schema deste tipo de evento, começando em `1` |
| `occurredAt` | Instant (ISO-8601) | Sim | Momento em que o evento ocorreu |
| `orderId` | String (formato UUID) | Sim, em **todo** evento | Identificador de correlação — o pedido de origem da transação de negócio |

**Rationale**: satisfaz FR-001 (correlação obrigatória via `orderId`) e FR-003 (schema mínimo
documentado, incluindo versionamento). `orderId` reaproveita o mesmo campo textual (validado
por formato UUID via `@Pattern`) já usado desde o item 1.4 em `payment-api`/`invoice-api` —
nenhum identificador de correlação novo é inventado. `eventVersion` como inteiro simples
(não um schema registry) é a estratégia mínima de versionamento decidida na Assumption da
spec — permite um consumidor detectar uma versão de payload que não reconhece e decidir como
reagir, sem exigir infraestrutura de registro de schema nesta etapa.

**Campos específicos de cada evento** (ex.: `customerId`/`amount` em `OrderCreated`,
`paymentId` em `PaymentReserved`) MUST ser decididos por cada serviço ao implementar seu
próprio produtor (E2.2-E2.4) — não são fixados por esta decisão, que documenta só o envelope
comum e a regra de correlação. Isso preserva FR-004/FR-007: esta feature não implementa
nenhum evento real, só o contrato que os torna consistentes entre si.

## Decisão 4: Nenhuma dependência de código compartilhada

**Decision**: a convenção documentada (Decisões 1-3) MUST ser implementada por cada serviço
através de sua própria classe de evento Java (ex.: `OrderCreatedEvent` em `order-api`) —
nenhum JAR/módulo comum de eventos é criado.

**Rationale**: consistente com o Princípio I (independência dos serviços), já validado como
requisito explícito da spec (FR-004) e do próprio ROADMAP ("Decisão documentada, não lib
compartilhada"). Cada serviço repete a mesma estrutura de campos (Decisão 3) de forma
independente — duplicação intencional, não acidental, pelo mesmo raciocínio já aplicado a
Model/DTO ao longo de toda a Fase 1.

## Conclusão

Esta feature não altera nenhum arquivo de produção — é puramente documentação de contrato,
consumida pelos itens seguintes (E2.2 implementa o produtor de `order-api` seguindo a
Decisão 1-3 pela primeira vez; E2.3/E2.4 replicam o mesmo padrão). Nenhuma dependência Maven
nova, nenhuma mudança de infraestrutura (Kafka já provisionado desde antes da Fase 1).
