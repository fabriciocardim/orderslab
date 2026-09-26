# Feature Specification: Convenção de Evento/Tópico Kafka

**Feature Branch**: `013-kafka-event-convention`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Convenção de evento/tópico Kafka para os 3 microsserviços (order-api, payment-api, invoice-api). Item E2.1 do ROADMAP.md, primeiro item da Fase 2 (Kafka assíncrono) — decisão que precisa existir antes de implementar qualquer produtor/consumidor (E2.2 order-api produtor, E2.3 payment-api consumidor+produtor, E2.4 invoice-api consumidor+produtor). Objetivo: decidir e documentar a convenção de nomeação de tópicos, o schema de payload de evento, e a estratégia de correlação (orderId presente em todo evento downstream). Decisão documentada (como o item 1.9), não uma biblioteca de eventos compartilhada (Princípio I exige independência). Fora de escopo: implementar produtor/consumidor real (E2.2-E2.4), estratégia de retry/dead-letter (E2.5), testes de mensageria (E2.6)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Todo evento pode ser rastreado até o pedido de origem (Priority: P1)

Como desenvolvedor do laboratório, quero que qualquer evento publicado por qualquer um dos 3
serviços (mesmo um evento sobre um pagamento ou uma nota, não diretamente sobre um pedido)
carregue o identificador do pedido de origem, para que eu consiga, ao implementar os
consumidores (E2.2-E2.4) e depois a observabilidade (Fase 6), reconstruir a jornada completa
de um pedido através dos 3 serviços a partir dos eventos que ele gerou.

**Why this priority**: é o valor central desta decisão — sem uma regra de correlação
obrigatória decidida *antes* de qualquer produtor existir, cada serviço poderia inventar sua
própria forma de referenciar o pedido, quebrando a possibilidade de reconstruir a jornada
completa mais tarde.

**Independent Test**: dado o payload de qualquer um dos 6 eventos já nomeados no ROADMAP
(`OrderCreated`/`OrderConfirmed`/`OrderCancelled`, `PaymentReserved`/`PaymentFailed`,
`InvoiceIssued`/`InvoiceFailed`), confirmar que a convenção documentada exige e localiza sem
ambiguidade o identificador do pedido de origem em cada um deles.

**Acceptance Scenarios**:

1. **Given** a convenção documentada, **When** um evento `PaymentReserved` ou `InvoiceIssued`
   é inspecionado (eventos que não são "sobre" um pedido diretamente), **Then** o
   identificador do pedido de origem está presente e é obrigatório, não opcional.
2. **Given** dois eventos de serviços diferentes que pertencem à mesma transação de negócio
   (ex.: `OrderCreated` e o `PaymentReserved` que ele desencadeou), **When** comparados,
   **Then** ambos carregam o mesmo identificador de pedido, permitindo agrupá-los.

---

### User Story 2 - Cada serviço implementa a convenção de forma independente, sem lib compartilhada (Priority: P2)

Como desenvolvedor do laboratório, quero que a convenção de evento/tópico seja documentada de
forma clara e completa o suficiente para que cada serviço a implemente por conta própria (nos
itens E2.2-E2.4), sem precisar consultar o código de outro serviço nem depender de uma
biblioteca de eventos compartilhada entre os 3.

**Why this priority**: preserva o Princípio I (independência dos serviços) — depende de US1
já existir (a convenção de correlação é parte do que precisa estar documentado), mas o valor
de "implementável sem lib compartilhada" é uma dimensão adicional sobre a mesma decisão.

**Independent Test**: entregar a documentação da convenção para os itens E2.2/E2.3/E2.4 e
confirmar que cada um consegue implementar seu evento sem precisar de nenhum artefato de
código novo compartilhado entre os 3 serviços.

**Acceptance Scenarios**:

1. **Given** a convenção documentada, **When** um novo tipo de evento precisa ser adicionado
   no futuro (além dos 6 já nomeados), **Then** um desenvolvedor consegue definir seu nome de
   tópico e schema de payload seguindo só a documentação, sem ambiguidade.
2. **Given** os 3 serviços, **When** cada um implementar seu próprio produtor/consumidor
   (E2.2-E2.4), **Then** nenhum deles precisa depender de uma biblioteca/JAR comum de
   eventos — cada um declara sua própria classe de evento seguindo a convenção.

---

### Edge Cases

- **Evolução de schema**: um evento existente precisa ganhar um campo novo no futuro sem
  quebrar consumidores que ainda esperam o formato antigo — a convenção MUST incluir uma
  estratégia mínima de versionamento (ex.: campo de versão no payload) para não travar essa
  evolução.
- **Formato de serialização**: o mecanismo exato (serializer/deserializer Kafka) usado para
  publicar/consumir os eventos MUST ser confirmado empiricamente para a versão real do
  Spring Boot/Spring Kafka em uso, não assumido a partir de conhecimento de versões
  anteriores — esta sessão já teve 3 surpresas de nomenclatura de artifact em decisões
  técnicas anteriores.
- **Consistência entre serviços**: se cada serviço decidisse sua própria convenção de nome de
  tópico/campo de correlação de forma independente, tópicos poderiam colidir em nome ou usar
  formatos de correlação incompatíveis entre si — a convenção documentada única evita isso
  sem exigir código compartilhado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Todo evento publicado por qualquer um dos 3 serviços MUST incluir um
  identificador do pedido de origem (correlação), mesmo quando o evento não é diretamente
  sobre a criação de um pedido.
- **FR-002**: A convenção de nomeação de tópicos MUST ser documentada de forma que se aplique
  de forma idêntica aos 6 eventos já nomeados no ROADMAP e a qualquer evento futuro.
- **FR-003**: O schema mínimo de payload que todo evento MUST ter (incluindo o identificador
  de correlação e um campo de versão de schema) MUST ser documentado.
- **FR-004**: A convenção documentada MUST ser implementável por cada serviço de forma
  independente — nenhuma biblioteca/dependência de código compartilhada entre os 3 serviços é
  introduzida por esta decisão (Princípio I da constitution).
- **FR-005**: O mecanismo de serialização/deserialização usado para publicar e consumir
  eventos MUST ser confirmado via resolução real de dependências para a versão em uso do
  Spring Boot/Spring Kafka, documentando a confirmação, não apenas assumido.
- **FR-006**: Esta decisão MUST ser documentada de forma consumível pelos itens seguintes
  (E2.2 produtor em order-api, E2.3 consumidor+produtor em payment-api, E2.4 consumidor+
  produtor em invoice-api) sem exigir que eles re-decidam qualquer parte da convenção.
- **FR-007**: Esta feature MUST NOT implementar nenhum produtor ou consumidor Kafka real,
  nenhuma estratégia de retry/dead-letter, e nenhum teste de mensageria — esse é escopo dos
  itens E2.2-E2.6, sequenciados depois.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dado o payload de qualquer um dos 6 eventos já nomeados no ROADMAP, um
  desenvolvedor consegue localizar o identificador do pedido de origem sem ambiguidade,
  usando só a documentação da convenção.
- **SC-002**: Dois eventos de serviços diferentes pertencentes à mesma transação de negócio
  compartilham o mesmo identificador de pedido, permitindo agrupá-los.
- **SC-003**: Um desenvolvedor consegue determinar o nome de tópico correto para qualquer um
  dos 6 eventos já nomeados, ou para um evento novo hipotético, usando só a convenção
  documentada.
- **SC-004**: Esta decisão introduz zero dependências de código novas compartilhadas entre os
  3 serviços.

## Assumptions

- O identificador de correlação é o `orderId` (já usado em `payment-api`/`invoice-api` como
  referência textual validada por formato desde o item 1.4) — reaproveitado como campo de
  correlação obrigatório em todo evento, não um identificador de correlação novo e distinto.
- O formato exato de nomeação de tópico (ex.: `dominio.evento` em minúsculas, ou outro
  padrão) e o serializer/deserializer exatos MUST ser decididos e confirmados empiricamente
  durante o planejamento técnico (`/speckit-plan`), seguindo o padrão já estabelecido nesta
  sessão (itens 1.1, 1.10, 1.11, 1.12) de não assumir nomes de artifact/configuração sem
  verificação real.
- Os 6 eventos já nomeados no ROADMAP (`OrderCreated`/`OrderConfirmed`/`OrderCancelled`,
  `PaymentReserved`/`PaymentFailed`, `InvoiceIssued`/`InvoiceFailed`) são o conjunto de
  referência usado para validar a convenção — a implementação real desses produtores/
  consumidores é escopo dos itens E2.2-E2.4, não desta feature.
- Versionamento de schema nesta etapa é mínimo (ex.: um campo de versão no payload) — uma
  estratégia completa de compatibilidade/registro de schema (ex.: Schema Registry) está fora
  de escopo, podendo ser revisitada se a necessidade real aparecer em fases futuras.
- O Kafka já está provisionado em `infra/docker-compose.yml`/`infra/k8s/` e
  `spring-boot-starter-kafka` já está presente nos 3 `pom.xml` desde o item 1.1 — nenhuma
  mudança de infraestrutura é necessária para esta decisão.
