# Feature Specification: order-api como Produtor Kafka

**Feature Branch**: `014-order-kafka-producer`

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "E2.2 — order-api como produtor Kafka: publicar OrderCreated/OrderConfirmed/OrderCancelled após cada transição de estado persistida, seguindo o contrato de evento/tópico definido no E2.1 (specs/013-kafka-event-convention/contracts/event-contract.md). Considerar outbox pattern para não violar o Princípio II (não publicar evento sem persistir a mudança, nem persistir sem publicar)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cada transição de pedido gera o evento correspondente, conforme o contrato (Priority: P1)

Como desenvolvedor do laboratório, quero que, ao criar, confirmar ou cancelar um pedido pela
API do `order-api`, o evento correspondente (`OrderCreated`, `OrderConfirmed` ou
`OrderCancelled`) apareça no tópico definido pelo contrato do E2.1, com o envelope obrigatório
preenchido, para que o `payment-api` (E2.3) possa começar a reagir a pedidos reais sem
precisar de nenhum ajuste no lado do `order-api`.

**Why this priority**: é o primeiro fluxo assíncrono real do laboratório. Sem ele, os itens
E2.3 e E2.4 não têm o que consumir, e a Fase 2 inteira fica bloqueada — mesmo que as garantias
de consistência (US2) e ordenação (US3) ainda estivessem pendentes.

**Independent Test**: com o `order-api` e o broker em execução, criar um pedido via API, depois
confirmá-lo (e, em outro pedido, cancelá-lo), e consumir os 3 tópicos para confirmar que cada
operação bem-sucedida produziu exatamente um evento com o envelope completo e o `orderId`
correto.

**Acceptance Scenarios**:

1. **Given** o `order-api` e o broker disponíveis, **When** um pedido é criado com sucesso,
   **Then** um evento `OrderCreated` é publicado em `order.created` com `eventId`, `eventType`,
   `eventVersion`, `occurredAt` e `orderId` preenchidos conforme o contrato do E2.1, mais os
   dados do pedido necessários para o próximo serviço da cadeia reagir.
2. **Given** um pedido em estado `PENDING`, **When** ele é confirmado com sucesso, **Then** um
   evento `OrderConfirmed` é publicado em `order.confirmed` com o `orderId` desse pedido.
3. **Given** um pedido em estado `PENDING`, **When** ele é cancelado com sucesso, **Then** um
   evento `OrderCancelled` é publicado em `order.cancelled` com o `orderId` desse pedido.
4. **Given** um evento publicado, **When** seu payload é inspecionado, **Then** `eventType`
   usa exatamente o nome da tabela do contrato (ex.: `"OrderCreated"`), `eventVersion` é `1` e
   `occurredAt` corresponde ao momento da transição de estado, não ao momento da entrega.

---

### User Story 2 - Estado persistido e evento publicado nunca divergem (Priority: P2)

Como desenvolvedor do laboratório, quero a garantia de que nunca existe um evento publicado
para uma transição que não foi persistida, nem uma transição persistida cujo evento se perde
para sempre — inclusive quando o broker está fora do ar ou o serviço cai entre persistir e
publicar — para que os consumidores (E2.3/E2.4) possam confiar que os eventos refletem o estado
real dos pedidos.

**Why this priority**: é o requisito que dá sentido técnico real ao fluxo assíncrono
(Princípio II da constitution: eventos "publicados corretamente", sem atalhos). Fica em P2
porque depende de US1 já emitir eventos, mas a feature só é considerada pronta quando esta
garantia vale — publicar sem ela seria entregar o fluxo "na aparência".

**Independent Test**: (a) derrubar o broker, executar operações pela API e confirmar que elas
respondem com sucesso e que, ao religar o broker, todos os eventos correspondentes chegam;
(b) provocar uma operação rejeitada (pedido inexistente, transição inválida, request inválido)
e confirmar que nenhum evento é publicado; (c) interromper o serviço logo após uma transição
ser persistida e confirmar que o evento ainda é entregue depois do restart.

**Acceptance Scenarios**:

1. **Given** o broker indisponível, **When** um pedido é criado, confirmado ou cancelado,
   **Then** a operação HTTP conclui com o mesmo resultado que teria com o broker disponível e a
   transição permanece persistida.
2. **Given** eventos de transições feitas enquanto o broker estava indisponível, **When** o
   broker volta a ficar disponível, **Then** todos esses eventos são entregues, sem perda.
3. **Given** uma operação rejeitada (pedido inexistente → 404, transição inválida → 409,
   request inválido → 400), **When** ela é processada, **Then** nenhum evento é publicado.
4. **Given** o serviço interrompido depois de persistir uma transição e antes de publicar o
   evento correspondente, **When** o serviço é reiniciado, **Then** o evento pendente é
   publicado.
5. **Given** dois pedidos de transição conflitantes chegando ao mesmo tempo para o mesmo pedido
   (ex.: confirmar e cancelar), **When** ambos são processados, **Then** apenas uma transição
   vale e apenas um evento — o do estado final — é publicado; nunca `OrderConfirmed` e
   `OrderCancelled` para o mesmo pedido.

---

### User Story 3 - Consumidores conseguem confiar na ordem e deduplicar entregas (Priority: P3)

Como desenvolvedor do laboratório, quero que os eventos de um mesmo pedido sejam consumíveis na
ordem em que as transições aconteceram, e que uma reentrega do mesmo evento seja reconhecível
como duplicata, para que os consumidores (E2.3/E2.4) sejam escritos sem precisar de lógica
defensiva contra reordenação ou processamento duplicado ambíguo.

**Why this priority**: refina o contrato de entrega depois que o fluxo básico (US1) e a
consistência (US2) existem. É a base de que o E2.3 precisa para tratar `OrderCreated` antes de
`OrderConfirmed`/`OrderCancelled`, mas não bloqueia a demonstração inicial do fluxo.

**Independent Test**: criar e confirmar (ou cancelar) vários pedidos em sequência e conferir,
por pedido, que `OrderCreated` precede `OrderConfirmed`/`OrderCancelled` ao consumir os
tópicos; forçar uma reentrega e confirmar que o `eventId` do evento repetido é idêntico ao do
original.

**Acceptance Scenarios**:

1. **Given** um pedido que foi criado e depois confirmado (ou cancelado), **When** os eventos
   desse pedido são consumidos, **Then** a ordem observada é a mesma ordem das transições
   (`OrderCreated` antes de `OrderConfirmed`/`OrderCancelled`).
2. **Given** um evento entregue mais de uma vez (reentrega após falha de confirmação de
   publicação), **When** as cópias são comparadas, **Then** todas carregam o mesmo `eventId`,
   permitindo ao consumidor descartar duplicatas.

---

### Edge Cases

- **Broker indisponível na hora da operação**: a operação HTTP não pode falhar nem ser
  revertida por causa disso (ver US2); o evento fica pendente até o broker voltar.
- **Broker indisponível por período prolongado**: eventos pendentes se acumulam sem perda e, ao
  recuperar, são entregues preservando a ordem por pedido (US3).
- **Falha entre persistir e publicar** (queda do serviço, encerramento forçado): o evento
  pendente sobrevive ao restart e é entregue depois.
- **Reentrega (at-least-once)**: a mesma transição pode resultar em mais de uma cópia do evento
  no tópico; todas as cópias compartilham o mesmo `eventId`.
- **Transições concorrentes sobre o mesmo pedido**: o código atual do `order-api` lê o estado,
  altera e salva sem controle de concorrência nem fronteira transacional explícita — duas
  requisições simultâneas (ex.: `confirm` e `cancel`) podem ambas enxergar `PENDING` e ambas
  "vencer". Publicar um evento por tentativa produziria eventos contraditórios; a feature MUST
  impedir isso (FR-009).
- **Operação rejeitada**: 404, 409 ou 400 não geram evento (FR-004).
- **Falha de serialização de um evento**: deve ficar visível (log de falha, FR-010), nunca
  silenciosa, e não pode bloquear a entrega dos demais eventos.
- **Crescimento do registro de eventos já entregues**: o armazenamento usado para garantir a
  entrega não pode crescer indefinidamente com eventos já entregues (política de limpeza é
  decisão do `/speckit-plan`).
- **Pedidos anteriores à feature**: pedidos que já existem no banco quando a feature entra em
  produção não geram eventos retroativos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O `order-api` MUST publicar um evento `OrderCreated` no tópico `order.created`
  após cada criação de pedido bem-sucedida.
- **FR-002**: O `order-api` MUST publicar `OrderConfirmed` em `order.confirmed` após cada
  confirmação bem-sucedida, e `OrderCancelled` em `order.cancelled` após cada cancelamento
  bem-sucedido.
- **FR-003**: Todo evento publicado MUST seguir o contrato do E2.1
  (`specs/013-kafka-event-convention/contracts/event-contract.md`): envelope completo
  (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `orderId`), `eventType` com o nome
  exato da tabela do contrato, `eventVersion` igual a `1`, nome de tópico conforme a tabela e
  mecanismo de serialização conforme o contrato. `occurredAt` MUST refletir o momento da
  transição de estado, não o da entrega.
- **FR-004**: Um evento MUST ser gerado apenas para uma transição efetivamente persistida;
  operações rejeitadas (404, 409, 400) MUST NOT gerar evento.
- **FR-005**: O estado persistido do pedido e o evento correspondente MUST NOT divergir:
  nunca pode existir evento sem a transição persistida, e toda transição persistida MUST
  resultar em um evento eventualmente entregue — inclusive se o serviço for interrompido entre
  persistir e publicar.
- **FR-006**: A indisponibilidade do broker MUST NOT fazer a operação HTTP falhar nem reverter
  a transição já persistida; o evento MUST ser entregue após a recuperação do broker.
- **FR-007**: A entrega MUST ser de pelo menos uma vez, com `eventId` estável: qualquer
  reentrega do mesmo evento carrega o mesmo `eventId` do original.
- **FR-008**: Os eventos de um mesmo pedido MUST ser consumíveis na mesma ordem em que as
  transições ocorreram.
- **FR-009**: Transições conflitantes concorrentes sobre o mesmo pedido MUST NOT produzir
  eventos contraditórios: exatamente uma transição prevalece e apenas o evento do estado final
  é publicado.
- **FR-010**: Cada tentativa de publicação (sucesso ou falha) MUST ser observável por log
  estruturado contendo `orderId`, `eventId` e `eventType`, correlacionado com `traceId`/
  `spanId` (Princípio VI, aproveitando a instrumentação do item 1.12); falhas persistentes de
  entrega MUST ficar visíveis, nunca silenciosas.
- **FR-011**: Os contratos HTTP existentes do `order-api` (rotas, payloads de request/response
  e códigos de status) MUST permanecer inalterados.
- **FR-012**: Nenhuma dependência de código compartilhada com `payment-api`/`invoice-api` pode
  ser introduzida (Princípio I); as classes de evento MUST ser declaradas dentro do próprio
  `order-api`.
- **FR-013**: A configuração necessária ao `order-api` para publicar MUST funcionar de forma
  consistente no Docker Compose e no cluster k8s local (Princípio IV), e o comportamento MUST
  ser validado empiricamente contra um Kafka real (subir o serviço, executar operações reais e
  consumir os tópicos), no mesmo padrão dos itens da Fase 1.
- **FR-014**: A suíte de testes automatizados do `order-api` MUST cobrir as garantias de
  FR-004, FR-005 e FR-009, e MUST continuar autossuficiente: `./mvnw test` não pode exigir
  infraestrutura do projeto previamente provisionada além de um runtime de contêiner
  disponível (mantendo o resultado do item 1.11).
- **FR-015**: Esta feature MUST NOT implementar nenhum consumidor Kafka (E2.3/E2.4), estratégia
  de retry/dead-letter de consumo (E2.5), suíte de testes de mensageria com Testcontainers
  Kafka (E2.6) ou propagação de contexto de trace pelos headers Kafka (E6.4), e MUST NOT
  alterar `payment-api`/`invoice-api`.

### Key Entities *(include if feature involves data)*

- **Pedido**: entidade já existente do `order-api` (identificador, cliente, valor, estado,
  datas). Cada transição persistida de estado é a origem de exatamente um evento.
- **Evento de pedido** (`OrderCreated`, `OrderConfirmed`, `OrderCancelled`): mensagem imutável
  com o envelope obrigatório do contrato do E2.1 (`eventId`, `eventType`, `eventVersion`,
  `occurredAt`, `orderId`) mais campos específicos do tipo de evento. É o único artefato
  visível para os consumidores.
- **Evento pendente de entrega**: registro persistido, na mesma unidade de consistência da
  transição do pedido, que representa um evento ainda não entregue ao broker. Sobrevive a
  restart do serviço e à indisponibilidade do broker, e mantém a ordem das transições de cada
  pedido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com o broker disponível, 100% das criações, confirmações e cancelamentos
  bem-sucedidos resultam em exatamente um evento correspondente observável no tópico correto,
  em até 5 segundos após a resposta da API.
- **SC-002**: 0 eventos publicados para operações rejeitadas (pedido inexistente, transição
  inválida, request inválido), verificado por todos os cenários de rejeição existentes.
- **SC-003**: Com o broker indisponível durante N operações, 100% delas respondem com o mesmo
  resultado que teriam com o broker disponível e, após a recuperação do broker, 100% dos
  eventos correspondentes são entregues, sem nenhuma perda.
- **SC-004**: Para qualquer pedido, 100% das observações dos seus eventos respeitam a ordem das
  transições (`OrderCreated` antes de `OrderConfirmed`/`OrderCancelled`).
- **SC-005**: 100% dos eventos publicados passam na conferência de conformidade com o contrato
  do E2.1 (5 campos do envelope presentes, `eventType` exato, tópico correto).
- **SC-006**: Sob um teste com transições conflitantes simultâneas no mesmo pedido, 0 casos de
  eventos contraditórios (`OrderConfirmed` e `OrderCancelled` para o mesmo `orderId`).
- **SC-007**: Um operador consegue, só pelos logs, localizar o evento de qualquer transição
  (`orderId`/`eventId`) e identificar qualquer falha de entrega.
- **SC-008**: Os contratos HTTP do `order-api` permanecem idênticos (mesmas rotas, payloads e
  códigos de status) e a instrumentação básica do item 1.12 continua funcionando.
- **SC-009**: Zero dependências de código novas compartilhadas entre os 3 serviços.

## Assumptions

- Esta feature é o item E2.2 do ROADMAP, na Fase 2 (Kafka assíncrono) — não antecipa nenhuma
  capacidade de fase futura. Consome diretamente o contrato do E2.1 e não o reabre.
- A escolha do mecanismo que garante FR-005/FR-006/FR-009 (o ROADMAP e o pedido original
  sugerem o *outbox pattern* como candidato) e a forma de controle de concorrência ficam para
  o `/speckit-plan`, com verificação empírica no padrão dos itens anteriores. Esta spec define
  apenas as garantias exigidas, não o mecanismo.
- Campos específicos dos eventos (decisão delegada ao E2.2 pelo contrato do E2.1): por padrão,
  `OrderCreated` carrega `customerId` e `amount` (o que o `payment-api` precisará para reservar
  o pagamento); `OrderConfirmed` e `OrderCancelled` carregam apenas o envelope. Nomes e tipos
  exatos são definidos no `/speckit-plan`.
- Entrega pelo menos uma vez, com deduplicação pelo `eventId` a cargo dos consumidores; entrega
  exatamente uma vez não é requisito.
- Ordem por pedido é garantida por pedido, não globalmente entre pedidos diferentes.
- Confirmar e cancelar pedidos continua sendo feito por chamadas REST explícitas; o
  fechamento automático da cadeia (`payment-api`/`invoice-api` reagindo e o pedido sendo
  confirmado/cancelado em resposta) depende de E2.3/E2.4 e está fora de escopo.
- O Kafka já está provisionado em `infra/docker-compose.yml` e `infra/k8s/`, e o `order-api` já
  tem `spring-boot-starter-kafka`, a property `spring.kafka.bootstrap-servers` e as env vars
  de bootstrap nos dois ambientes — espera-se pouca ou nenhuma mudança de infraestrutura; a
  necessidade de criar/provisionar os tópicos explicitamente é uma verificação do
  `/speckit-plan`.
- O `order-api` já persiste o estado real em Postgres via JPA (item 1.10) e tem logging
  estruturado com `traceId`/`spanId` (item 1.12); ambos são pré-requisitos já atendidos.
- Não há backfill: pedidos existentes antes da feature não geram eventos retroativos.
