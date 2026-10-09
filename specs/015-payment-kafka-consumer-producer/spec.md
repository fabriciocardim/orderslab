# Feature Specification: payment-api como Consumidor e Produtor Kafka

**Feature Branch**: `015-payment-kafka-consumer-producer`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "E2.3 — payment-api como consumidor+produtor Kafka: consumir OrderCreated do tópico order.created (contrato do E2.1 e eventos do E2.2) e, para cada pedido, criar/decidir o pagamento com regra simples e determinística de sucesso/falha, publicando PaymentReserved em payment.reserved ou PaymentFailed em payment.failed, com o envelope obrigatório. Garantias: consumo idempotente por eventId, consistência entre estado persistido e evento publicado (outbox próprio do payment-api, sem código compartilhado), broker indisponível não derruba o serviço nem perde eventos, ordem por pedido preservada, logs estruturados, tópicos declarados explicitamente, configuração consistente em Compose e k8s, validação empírica contra Kafka e Postgres reais. Contratos HTTP existentes inalterados. Fora de escopo: invoice-api (E2.4), retry/dead-letter (E2.5), Testcontainers Kafka (E2.6), propagação de trace por headers (E6.4), qualquer mudança no order-api."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Um pedido criado gera automaticamente uma decisão de pagamento publicada como evento (Priority: P1)

Como desenvolvedor do laboratório, quero que, quando o `order-api` publica `OrderCreated`, o
`payment-api` reaja sozinho: registre um pagamento para aquele pedido, decida de forma simples
e previsível se ele é reservado ou falha, e publique o resultado (`PaymentReserved` ou
`PaymentFailed`) conforme o contrato do E2.1, para que o `invoice-api` (E2.4) possa reagir a
pagamentos reais sem nenhum ajuste no `payment-api`.

**Why this priority**: é o segundo elo da cadeia assíncrona. Sem ele o fluxo termina em
`order.created` e o E2.4 não tem o que consumir.

**Independent Test**: com `order-api`, `payment-api` e o broker em execução, criar um pedido de
valor baixo e outro de valor acima do limite pela API do `order-api`; consumir `payment.reserved`
e `payment.failed` e confirmar que cada pedido gerou exatamente um evento do tipo esperado, com
o `orderId` correto; consultar `GET /api/payments` e ver um pagamento por pedido.

**Acceptance Scenarios**:

1. **Given** um `OrderCreated` com valor dentro do limite de aprovação, **When** o
   `payment-api` o processa, **Then** é persistido um pagamento do pedido com situação
   "reservado" e um `PaymentReserved` é publicado em `payment.reserved` com o envelope completo
   (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `orderId`) mais o identificador e o valor
   do pagamento.
2. **Given** um `OrderCreated` com valor acima do limite de aprovação, **When** o
   `payment-api` o processa, **Then** é persistido um pagamento do pedido com situação "falhou"
   e um `PaymentFailed` é publicado em `payment.failed` com o envelope completo mais o motivo da
   falha.
3. **Given** a mesma entrada (`orderId` e valor), **When** processada em execuções diferentes,
   **Then** o resultado (reservado ou falhou) é sempre o mesmo — a regra é determinística.
4. **Given** um evento publicado, **When** seu payload é inspecionado, **Then** `eventType` usa
   exatamente o nome do contrato (`"PaymentReserved"`/`"PaymentFailed"`), `eventVersion` é `1`,
   `occurredAt` corresponde ao momento da decisão e `orderId` é o do pedido de origem (não o id do
   pagamento).

---

### User Story 2 - Reentregas e falhas de infraestrutura não duplicam nem perdem resultados (Priority: P2)

Como desenvolvedor do laboratório, quero a garantia de que um mesmo `OrderCreated` entregue mais
de uma vez nunca produz dois pagamentos nem dois eventos, e de que uma decisão de pagamento
persistida nunca fica sem seu evento — mesmo com o broker fora do ar ou o serviço caindo no meio
do processamento — para que os consumidores a jusante (E2.4) possam confiar no estado real.

**Why this priority**: a entrega do Kafka é pelo menos uma vez; sem esta garantia o fluxo só
funciona no caminho feliz. Depende de US1 já decidir e publicar, mas a feature só está pronta
quando ela vale.

**Independent Test**: (a) reenviar o mesmo `OrderCreated` (mesmo `eventId`) e verificar um único
pagamento e um único evento de saída; (b) derrubar o broker, criar pedidos, religar e verificar
que todos os resultados chegam; (c) interromper o serviço à força logo após persistir uma decisão
e confirmar que o evento correspondente sai depois do restart.

**Acceptance Scenarios**:

1. **Given** um `OrderCreated` já processado, **When** o mesmo evento (mesmo `eventId`) é
   entregue novamente, **Then** nenhum novo pagamento é criado e nenhum novo evento de saída é
   publicado.
2. **Given** dois `OrderCreated` distintos (`eventId` diferentes) para o mesmo `orderId`,
   **When** ambos são processados, **Then** existe no máximo um pagamento para esse pedido e no
   máximo um evento de resultado.
3. **Given** o broker indisponível na hora em que o resultado precisa ser publicado, **When** o
   pagamento é decidido, **Then** a decisão fica persistida, o serviço continua saudável e,
   quando o broker volta, o evento é entregue sem perda.
4. **Given** o serviço interrompido depois de persistir a decisão e antes de publicar o evento,
   **When** é reiniciado, **Then** o evento pendente é publicado.
5. **Given** uma falha ao processar um `OrderCreated` (ex.: erro ao gravar), **When** o evento é
   reentregue, **Then** nenhum estado parcial permanece: ou o pagamento e seu evento pendente
   foram gravados juntos, ou nenhum dos dois.

---

### User Story 3 - Resultados de um mesmo pedido são consumíveis na ordem e deduplicáveis (Priority: P3)

Como desenvolvedor do laboratório, quero que os eventos de saída de um mesmo pedido mantenham a
ordem em que foram decididos e que uma reentrega de um evento de saída seja reconhecível pelo
`eventId`, para que o E2.4 seja escrito sem lógica defensiva contra reordenação ou duplicata
ambígua.

**Why this priority**: refina o contrato de entrega depois do fluxo básico (US1) e da consistência
(US2); espelha a garantia que o E2.2 deu ao lado do pedido.

**Independent Test**: processar vários pedidos em sequência e conferir, por pedido, a ordem
observada nos tópicos de resultado; forçar uma reentrega do evento de saída e conferir `eventId`
idêntico ao original.

**Acceptance Scenarios**:

1. **Given** vários pedidos processados em sequência, **When** os eventos de saída de cada pedido
   são consumidos, **Then** a ordem observada por pedido é a ordem em que as decisões foram
   tomadas.
2. **Given** um evento de saída entregue mais de uma vez, **When** as cópias são comparadas,
   **Then** todas carregam o mesmo `eventId`.

---

### Edge Cases

- **Reentrega do mesmo `OrderCreated`**: tratada como duplicata e ignorada, sem efeito colateral
  (FR-004).
- **Dois `OrderCreated` com `eventId` diferentes para o mesmo pedido**: o segundo não gera novo
  pagamento (FR-005).
- **Broker indisponível**: o serviço permanece em pé, o processamento persiste a decisão, e os
  eventos de saída ficam pendentes até o broker voltar (FR-008).
- **Mensagem de entrada inválida ou ilegível** (JSON malformado, campo obrigatório ausente):
  deve ficar visível em log e não pode travar o consumo das mensagens seguintes nem derrubar o
  serviço; a estratégia completa de retry/dead-letter é o E2.5 (FR-011).
- **Valor exatamente no limite de aprovação**: tratado como dentro do limite (reservado).
- **Pedidos anteriores à feature**: eventos `OrderCreated` já presentes no tópico quando o
  `payment-api` é implantado pela primeira vez são processados normalmente (o consumidor lê o
  tópico desde o início na primeira execução).
- **Pagamentos criados pela API REST existente**: continuam funcionando como hoje e não geram
  eventos nesta feature (FR-012).
- **Crescimento do registro de eventos de saída pendentes e de eventos já processados**: o
  armazenamento usado para garantir entrega e deduplicação não pode crescer sem controle no caso
  do registro de saída (já entregues); a política para o registro de deduplicação é decisão do
  `/speckit-plan`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O `payment-api` MUST consumir os eventos `OrderCreated` do tópico `order.created`
  conforme o contrato do E2.1 e os campos definidos pelo E2.2 (`orderId`, `customerId`,
  `amount`), sem depender de código do `order-api` (Princípio I).
- **FR-002**: Para cada `OrderCreated` novo, o `payment-api` MUST registrar um pagamento do
  pedido e decidi-lo por uma regra simples e determinística baseada no valor do pedido: valor
  acima de um limite de aprovação configurável resulta em falha; caso contrário, em reserva.
  O mesmo `orderId` e valor MUST sempre produzir o mesmo resultado.
- **FR-003**: Ao decidir, o `payment-api` MUST publicar `PaymentReserved` em `payment.reserved`
  (reserva) ou `PaymentFailed` em `payment.failed` (falha). Todo evento MUST seguir o contrato do
  E2.1: envelope completo (`eventId`, `eventType`, `eventVersion` = 1, `occurredAt`, `orderId`),
  `eventType` com o nome exato da tabela do contrato, `orderId` do pedido de origem e
  `occurredAt` refletindo o momento da decisão. `PaymentReserved` MUST carregar também o
  identificador e o valor do pagamento; `PaymentFailed` MUST carregar o motivo da falha.
- **FR-004**: O consumo MUST ser idempotente por `eventId`: o mesmo `OrderCreated` entregue
  novamente MUST NOT criar outro pagamento nem publicar outro evento de saída.
- **FR-005**: Para um mesmo pedido MUST existir no máximo um pagamento decidido a partir de
  eventos, mesmo que cheguem `OrderCreated` distintos para o mesmo `orderId`.
- **FR-006**: O estado persistido do pagamento e o evento de saída correspondente MUST NOT
  divergir: nunca pode existir evento sem a decisão persistida, e toda decisão persistida MUST
  resultar em um evento eventualmente entregue, inclusive se o serviço for interrompido entre
  persistir e publicar. A confirmação de que um `OrderCreated` foi tratado MUST ocorrer junto
  com a persistência da decisão e do evento pendente, de forma atômica.
- **FR-007**: A entrega de eventos de saída MUST ser de pelo menos uma vez com `eventId` estável
  (reentregas carregam o mesmo `eventId`), e os eventos de um mesmo pedido MUST ser consumíveis
  na ordem em que as decisões foram tomadas (chave de mensagem = `orderId`).
- **FR-008**: A indisponibilidade do broker MUST NOT derrubar o serviço, nem fazer o
  processamento perder decisões; os eventos de saída MUST ser entregues após a recuperação do
  broker.
- **FR-009**: Cada tentativa de processamento e de publicação (sucesso ou falha) MUST ser
  observável por log estruturado contendo `orderId`, `eventId` e `eventType`, correlacionado com
  `traceId`/`spanId` (Princípio VI); falhas persistentes MUST ficar visíveis, nunca silenciosas.
- **FR-010**: Os tópicos `payment.reserved` e `payment.failed` MUST ser declarados
  explicitamente pelo `payment-api` com 3 partições e 1 réplica, em vez de depender da criação
  automática do broker (Princípio IV).
- **FR-011**: Uma mensagem de entrada inválida (ilegível ou sem campos obrigatórios) MUST ser
  registrada em log com contexto suficiente para diagnóstico e MUST NOT bloquear o consumo das
  mensagens seguintes nem derrubar o serviço. Retry e dead-letter completos ficam fora desta
  feature (E2.5).
- **FR-012**: Os contratos HTTP existentes do `payment-api` (rotas, payloads e códigos de status
  de `POST/GET /api/payments` e transições `confirm`/`cancel`) MUST permanecer inalterados. A
  situação "falhou" é um valor novo que pode aparecer nas respostas de consulta de pagamentos
  criados por esta feature. Pagamentos criados pela API REST não geram eventos nesta feature.
- **FR-013**: Nenhuma dependência de código compartilhada com `order-api`/`invoice-api` pode ser
  introduzida (Princípio I); as classes de evento de entrada e de saída MUST ser declaradas
  dentro do próprio `payment-api`.
- **FR-014**: A configuração necessária ao `payment-api` para consumir e publicar MUST funcionar
  de forma consistente no Docker Compose e no cluster k8s local (Princípio IV), e o
  comportamento MUST ser validado empiricamente contra Kafka e Postgres reais (subir os serviços,
  criar pedidos pelo `order-api` e observar o resultado nos tópicos), no mesmo padrão do E2.2.
- **FR-015**: A suíte automatizada do `payment-api` MUST cobrir FR-002, FR-004, FR-005 e FR-006
  e MUST continuar autossuficiente: `./mvnw test` não pode exigir infraestrutura do projeto
  previamente provisionada além de um runtime de contêiner (mantendo o resultado do item 1.11).
- **FR-016**: Esta feature MUST NOT implementar o `invoice-api` (E2.4), estratégia de
  retry/dead-letter de consumo (E2.5), suíte Testcontainers Kafka (E2.6) ou propagação de
  contexto de trace por headers Kafka (E6.4), e MUST NOT alterar o `order-api`.

### Key Entities *(include if feature involves data)*

- **Pagamento**: entidade já existente do `payment-api` (identificador, pedido, valor, situação,
  datas). Ganha a situação "falhou" além de reservado/confirmado/cancelado. Cada `OrderCreated`
  novo origina exatamente um pagamento decidido.
- **Evento de pagamento** (`PaymentReserved`, `PaymentFailed`): mensagem imutável com o envelope
  do contrato do E2.1 mais campos próprios do tipo (identificador e valor no reservado; motivo
  no falho). É o único artefato visível para os consumidores (E2.4).
- **Evento de pedido recebido** (`OrderCreated`): entrada do consumo; identificado pelo
  `eventId` para deduplicação.
- **Evento pendente de entrega**: registro persistido, na mesma unidade de consistência da
  decisão do pagamento, que representa um evento de saída ainda não entregue ao broker.
- **Evento já processado**: registro persistido que associa um `eventId` de entrada a um
  tratamento concluído, usado para ignorar reentregas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com broker e serviços disponíveis, 100% dos pedidos criados resultam em exatamente
  um evento de resultado de pagamento (`PaymentReserved` ou `PaymentFailed`) observável no tópico
  correto em até 10 segundos após a criação do pedido.
- **SC-002**: 100% dos pedidos com valor acima do limite resultam em `PaymentFailed` e 100% dos
  pedidos com valor dentro do limite resultam em `PaymentReserved`, de forma repetível em
  execuções diferentes.
- **SC-003**: Reentregar N vezes o mesmo evento de pedido resulta em 0 pagamentos extras e 0
  eventos de saída extras.
- **SC-004**: Com o broker indisponível durante N pedidos, 0 pedidos são perdidos: após a
  recuperação, 100% dos resultados correspondentes são entregues e o serviço permanece saudável
  durante toda a indisponibilidade.
- **SC-005**: Após interrupção forçada do serviço entre persistir e publicar, 100% dos eventos
  pendentes são entregues depois do restart.
- **SC-006**: Para qualquer pedido, 100% das observações dos eventos de resultado respeitam a
  ordem das decisões, e 100% dos eventos publicados passam na conferência de conformidade com o
  contrato do E2.1 (5 campos do envelope, `eventType` exato, tópico correto).
- **SC-007**: Um operador consegue, só pelos logs, localizar o processamento e a publicação de
  qualquer pedido (`orderId`/`eventId`) e identificar qualquer falha de entrega.
- **SC-008**: Os contratos HTTP do `payment-api` permanecem idênticos (mesmas rotas, payloads e
  códigos de status) e a instrumentação básica do item 1.12 continua funcionando.
- **SC-009**: Zero dependências de código novas compartilhadas entre os 3 serviços e zero
  alterações no `order-api`.

## Assumptions

- Esta feature é o item E2.3 do ROADMAP, na Fase 2 (Kafka assíncrono); consome o contrato do
  E2.1 e os eventos do E2.2 sem reabri-los.
- Regra de decisão (padrão): o pagamento falha quando o valor do pedido é **estritamente maior
  que 1000.00**; caso contrário é reservado. O limite é configurável. O motivo de falha usa um
  código estável (ex.: `AMOUNT_LIMIT_EXCEEDED`). Nomes e tipos exatos dos campos específicos dos
  eventos são definidos no `/speckit-plan`.
- Um pagamento "falhou" é persistido (com a situação "falhou") para manter rastreabilidade de
  cada pedido processado; ele não passa por `confirm`/`cancel`.
- O mecanismo que garante FR-004 a FR-008 (o E2.2 usou outbox transacional + relay, e o pedido
  original sugere o mesmo padrão) e o mecanismo de deduplicação ficam para o `/speckit-plan`,
  com verificação empírica no padrão dos itens anteriores. Esta spec define só as garantias.
- Entrega pelo menos uma vez, com deduplicação pelo `eventId` a cargo dos consumidores;
  exatamente uma vez não é requisito. Ordem garantida por pedido, não globalmente.
- O consumidor lê `order.created` desde o início na primeira execução (grupo novo), de modo que
  pedidos criados antes da implantação do `payment-api` com a feature também são tratados.
- O Kafka e as variáveis de bootstrap do `payment-api` já existem em Compose e k8s; espera-se
  pouca ou nenhuma mudança de infraestrutura. A necessidade de ajustes é verificação do plan.
- O `payment-api` já persiste em Postgres via JPA (item 1.10), tem logging estruturado com
  `traceId`/`spanId` (item 1.12) e a dependência do starter Kafka; são pré-requisitos atendidos.
- Sem backfill além da leitura do tópico; sem tratamento de moeda ou múltiplos pagamentos por
  pedido.
