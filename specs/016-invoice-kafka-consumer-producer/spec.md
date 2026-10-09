# Feature Specification: invoice-api como Consumidor e Produtor Kafka

**Feature Branch**: `016-invoice-kafka-consumer-producer`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "E2.4 — invoice-api como consumidor+produtor Kafka: consumir PaymentReserved do tópico payment.reserved (contrato do E2.1 e eventos do E2.3) e, para cada pedido, emitir/decidir a nota fiscal com regra simples e determinística de sucesso/falha, publicando InvoiceIssued em invoice.issued ou InvoiceFailed em invoice.failed com o envelope obrigatório. PaymentFailed não é consumido. Garantias já validadas no E2.2/E2.3: consumo idempotente por eventId e no máximo uma nota por pedido originada de evento; consistência estado×evento via outbox próprio, sem código compartilhado; broker indisponível não derruba o serviço nem perde eventos; falha transitória não descarta registro e mensagem ilegível é logada e pulada; ordem por pedido; logs estruturados; tópicos declarados explicitamente; configuração consistente em Compose e k8s; validação empírica incluindo o fluxo ponta a ponta. Contratos HTTP do invoice-api inalterados. Fora de escopo: retry limitado/dead-letter (E2.5), Testcontainers Kafka (E2.6), propagação de trace por headers (E6.4), mudanças no order-api ou payment-api."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Um pagamento reservado gera automaticamente uma decisão de nota fiscal publicada como evento (Priority: P1)

Como desenvolvedor do laboratório, quero que, quando o `payment-api` publica `PaymentReserved`, o
`invoice-api` reaja sozinho: registre uma nota fiscal para aquele pedido, decida de forma simples
e previsível se ela é emitida ou falha, e publique o resultado (`InvoiceIssued` ou
`InvoiceFailed`) conforme o contrato do E2.1, fechando a cadeia assíncrona
`order-api → payment-api → invoice-api`.

**Why this priority**: é o último elo da cadeia de eventos da Fase 2. Sem ele o fluxo termina em
`payment.reserved` e nenhum pedido chega a ter nota.

**Independent Test**: com os três serviços e o broker em execução, criar um pedido de valor baixo
e outro de valor intermediário pela API do `order-api`; consumir `invoice.issued` e
`invoice.failed` e confirmar que cada pedido gerou exatamente um evento do tipo esperado, com o
`orderId` correto; consultar `GET /api/invoices` e ver uma nota por pedido. Um pedido cujo pagamento
falhou não gera nota.

**Acceptance Scenarios**:

1. **Given** um `PaymentReserved` com valor dentro do limite de emissão, **When** o `invoice-api`
   o processa, **Then** é persistida uma nota do pedido com situação "emitida" e um
   `InvoiceIssued` é publicado em `invoice.issued` com o envelope completo (`eventId`,
   `eventType`, `eventVersion`, `occurredAt`, `orderId`) mais o identificador da nota, o do
   pagamento e o valor.
2. **Given** um `PaymentReserved` com valor acima do limite de emissão, **When** o `invoice-api`
   o processa, **Then** é persistida uma nota do pedido com situação "falhou" e um
   `InvoiceFailed` é publicado em `invoice.failed` com o envelope completo mais o identificador do
   pagamento e o motivo da falha.
3. **Given** a mesma entrada (`orderId`, `paymentId` e valor), **When** processada em execuções
   diferentes, **Then** o resultado (emitida ou falhou) é sempre o mesmo — a regra é determinística.
4. **Given** um evento publicado, **When** seu payload é inspecionado, **Then** `eventType` usa
   exatamente o nome do contrato (`"InvoiceIssued"`/`"InvoiceFailed"`), `eventVersion` é `1`,
   `occurredAt` corresponde ao momento da decisão e `orderId` é o do pedido de origem (não o id da
   nota).
5. **Given** um `PaymentFailed` publicado em `payment.failed`, **When** o `invoice-api` está em
   execução, **Then** nenhuma nota é criada e nenhum evento de nota é publicado para esse pedido.

---

### User Story 2 - Reentregas e falhas de infraestrutura não duplicam nem perdem resultados (Priority: P2)

Como desenvolvedor do laboratório, quero a garantia de que um mesmo `PaymentReserved` entregue
mais de uma vez nunca produz duas notas nem dois eventos, e de que uma decisão persistida nunca
fica sem seu evento — mesmo com o broker ou o banco fora do ar, ou o serviço caindo no meio do
processamento — para que o estado da nota seja confiável e a cadeia não perca pedidos.

**Why this priority**: a entrega do Kafka é pelo menos uma vez; sem esta garantia o fluxo só
funciona no caminho feliz. Depende de US1, mas a feature só está pronta quando ela vale.

**Independent Test**: (a) reenviar o mesmo `PaymentReserved` (mesmo `eventId`) e verificar uma
única nota e um único evento; (b) derrubar o broker, criar pedidos, religar e verificar que todos
os resultados chegam; (c) interromper o serviço à força logo após persistir uma decisão e
confirmar que o evento sai depois do restart; (d) derrubar o banco, enviar eventos, religar e
verificar que nenhum foi perdido.

**Acceptance Scenarios**:

1. **Given** um `PaymentReserved` já processado, **When** o mesmo evento (mesmo `eventId`) é
   entregue novamente, **Then** nenhuma nova nota é criada e nenhum novo evento é publicado.
2. **Given** dois `PaymentReserved` distintos (`eventId` diferentes) para o mesmo `orderId`,
   **When** ambos são processados, **Then** existe no máximo uma nota para esse pedido e no máximo
   um evento de resultado.
3. **Given** o broker indisponível quando o resultado precisa ser publicado, **When** a nota é
   decidida, **Then** a decisão fica persistida, o serviço continua saudável e, quando o broker
   volta, o evento é entregue sem perda.
4. **Given** o serviço interrompido depois de persistir a decisão e antes de publicar, **When** é
   reiniciado, **Then** o evento pendente é publicado.
5. **Given** uma falha transitória ao processar um evento (ex.: banco indisponível), **When** a
   falha persiste por algum tempo, **Then** nenhum evento é descartado e, quando a falha cessa, o
   evento é processado normalmente.
6. **Given** uma falha ao gravar durante o processamento, **When** o evento é reentregue, **Then**
   nenhum estado parcial permanece: a nota e seu evento pendente foram gravados juntos ou nenhum
   dos dois.

---

### User Story 3 - Resultados de um mesmo pedido são consumíveis na ordem e deduplicáveis (Priority: P3)

Como desenvolvedor do laboratório, quero que os eventos de saída de um mesmo pedido mantenham a
ordem em que foram decididos e que uma reentrega de um evento de saída seja reconhecível pelo
`eventId`, para que futuros consumidores (ex.: orquestração da Fase 3) não precisem de lógica
defensiva contra reordenação ou duplicata ambígua.

**Why this priority**: refina o contrato de entrega depois do fluxo básico e da consistência;
espelha a garantia dada nos itens E2.2 e E2.3.

**Independent Test**: processar vários pedidos em sequência e conferir, por pedido, a ordem
observada nos tópicos de resultado; forçar uma reentrega de saída e conferir `eventId` idêntico.

**Acceptance Scenarios**:

1. **Given** vários pedidos processados em sequência, **When** os eventos de saída de cada pedido
   são consumidos, **Then** a ordem observada por pedido é a ordem em que as decisões foram
   tomadas.
2. **Given** um evento de saída entregue mais de uma vez, **When** as cópias são comparadas,
   **Then** todas carregam o mesmo `eventId`.

---

### Edge Cases

- **Reentrega do mesmo `PaymentReserved`**: tratada como duplicata e ignorada (FR-004).
- **Dois `PaymentReserved` com `eventId` diferentes para o mesmo pedido**: o segundo não gera
  nova nota (FR-005).
- **Pagamento que falhou**: `PaymentFailed` não é consumido; o pedido fica sem nota (FR-001).
- **Broker indisponível**: o serviço permanece em pé; os eventos de saída ficam pendentes até o
  broker voltar (FR-008).
- **Banco indisponível**: o consumo não descarta registros; processa quando o banco volta (FR-008).
- **Mensagem de entrada inválida ou ilegível** (JSON malformado, campo obrigatório ausente, valor
  não positivo): registrada em log e pulada, sem travar o consumo das seguintes nem derrubar o
  serviço; estratégia completa de retry/dead-letter é o E2.5 (FR-011).
- **Valor exatamente no limite de emissão**: tratado como dentro do limite (emitida).
- **Notas criadas pela API REST existente**: continuam funcionando como hoje e não geram eventos
  (FR-012).
- **Eventos já presentes no tópico quando o serviço é implantado pela primeira vez**: processados
  normalmente (leitura desde o início na primeira execução).
- **Crescimento do registro de eventos de saída pendentes**: o armazenamento usado para garantir a
  entrega não pode crescer sem controle com eventos já entregues.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O `invoice-api` MUST consumir os eventos `PaymentReserved` do tópico
  `payment.reserved` conforme o contrato do E2.1 e os campos definidos pelo E2.3 (`orderId`,
  `paymentId`, `amount`), sem depender de código de outro serviço (Princípio I). Eventos
  `PaymentFailed` MUST NOT ser consumidos nem gerar nota.
- **FR-002**: Para cada `PaymentReserved` novo, o `invoice-api` MUST registrar uma nota do pedido
  e decidi-la por regra simples e determinística baseada no valor: valor acima de um limite de
  emissão configurável resulta em falha; caso contrário, em emissão. O mesmo `orderId`,
  `paymentId` e valor MUST sempre produzir o mesmo resultado. Não há cálculo de imposto real.
- **FR-003**: Ao decidir, o `invoice-api` MUST publicar `InvoiceIssued` em `invoice.issued`
  (emissão) ou `InvoiceFailed` em `invoice.failed` (falha). Todo evento MUST seguir o contrato do
  E2.1: envelope completo (`eventId`, `eventType`, `eventVersion` = 1, `occurredAt`, `orderId`),
  `eventType` com o nome exato da tabela do contrato, `orderId` do pedido de origem e `occurredAt`
  refletindo o momento da decisão. `InvoiceIssued` MUST carregar também o identificador da nota, o
  do pagamento e o valor; `InvoiceFailed` MUST carregar o identificador do pagamento e o motivo.
- **FR-004**: O consumo MUST ser idempotente por `eventId`: o mesmo `PaymentReserved` entregue
  novamente MUST NOT criar outra nota nem publicar outro evento de saída.
- **FR-005**: Para um mesmo pedido MUST existir no máximo uma nota decidida a partir de eventos,
  mesmo que cheguem `PaymentReserved` distintos para o mesmo `orderId`.
- **FR-006**: O estado persistido da nota e o evento de saída correspondente MUST NOT divergir:
  nunca pode existir evento sem a decisão persistida, e toda decisão persistida MUST resultar em
  um evento eventualmente entregue, inclusive se o serviço for interrompido entre persistir e
  publicar. A confirmação de que um `PaymentReserved` foi tratado MUST ocorrer junto com a
  persistência da decisão e do evento pendente, de forma atômica.
- **FR-007**: A entrega de saída MUST ser de pelo menos uma vez com `eventId` estável, e os
  eventos de um mesmo pedido MUST ser consumíveis na ordem das decisões (chave = `orderId`).
- **FR-008**: A indisponibilidade do broker MUST NOT derrubar o serviço nem perder decisões; os
  eventos de saída MUST ser entregues após a recuperação. Uma falha transitória de consumo
  (ex.: banco indisponível) MUST NOT descartar o registro: ele é reprocessado até a falha cessar.
- **FR-009**: Cada tentativa de processamento e de publicação (sucesso ou falha) MUST ser
  observável por log estruturado contendo `orderId`, `eventId` e `eventType`, correlacionado com
  `traceId`/`spanId` (Princípio VI); falhas persistentes MUST ficar visíveis, nunca silenciosas.
- **FR-010**: Os tópicos `invoice.issued` e `invoice.failed`, além de `payment.reserved` (que o
  serviço consome), MUST ser declarados explicitamente pelo `invoice-api` com 3 partições e
  1 réplica, em vez de depender da criação automática do broker (Princípio IV).
- **FR-011**: Uma mensagem de entrada inválida (ilegível, sem campos obrigatórios ou com valor não
  positivo) MUST ser registrada em log com contexto suficiente para diagnóstico e MUST NOT
  bloquear o consumo das mensagens seguintes nem derrubar o serviço. Retry limitado e dead-letter
  ficam fora desta feature (E2.5).
- **FR-012**: Os contratos HTTP existentes do `invoice-api` (rotas, payloads e códigos de status
  de `POST/GET /api/invoices` e transições `issue`/`cancel`) MUST permanecer inalterados. A
  situação "falhou" é um valor novo que pode aparecer nas respostas de consulta de notas criadas
  por esta feature. Notas criadas pela API REST não geram eventos.
- **FR-013**: Nenhuma dependência de código compartilhada com `order-api`/`payment-api` pode ser
  introduzida (Princípio I); as classes de evento de entrada e de saída MUST ser declaradas dentro
  do próprio `invoice-api`.
- **FR-014**: A configuração necessária ao `invoice-api` para consumir e publicar MUST funcionar
  de forma consistente no Docker Compose e no cluster k8s local (Princípio IV), e o comportamento
  MUST ser validado empiricamente contra Kafka e Postgres reais, incluindo o fluxo ponta a ponta
  `order-api → payment-api → invoice-api` (criar pedido e observar o resultado em todos os
  tópicos).
- **FR-015**: A suíte automatizada do `invoice-api` MUST cobrir FR-002, FR-004, FR-005 e FR-006 e
  MUST continuar autossuficiente: `./mvnw test` não pode exigir infraestrutura do projeto
  previamente provisionada além de um runtime de contêiner.
- **FR-016**: Esta feature MUST NOT implementar retry limitado/dead-letter de consumo (E2.5),
  suíte Testcontainers Kafka (E2.6) ou propagação de contexto de trace por headers Kafka (E6.4),
  e MUST NOT alterar `order-api` nem `payment-api`.

### Key Entities *(include if feature involves data)*

- **Nota fiscal**: entidade já existente do `invoice-api` (identificador, pedido, pagamento, valor,
  situação, datas). Ganha a situação "falhou" além de pendente/emitida/cancelada. Cada
  `PaymentReserved` novo origina exatamente uma nota decidida.
- **Evento de nota** (`InvoiceIssued`, `InvoiceFailed`): mensagem imutável com o envelope do
  contrato do E2.1 mais campos próprios do tipo. É o único artefato visível para consumidores.
- **Evento de pagamento recebido** (`PaymentReserved`): entrada do consumo; identificado pelo
  `eventId` para deduplicação.
- **Evento pendente de entrega**: registro persistido, na mesma unidade de consistência da
  decisão da nota, que representa um evento de saída ainda não entregue ao broker.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com broker e serviços disponíveis, 100% dos pedidos cujo pagamento foi reservado
  resultam em exatamente um evento de resultado de nota (`InvoiceIssued` ou `InvoiceFailed`)
  observável no tópico correto em até 15 segundos após a criação do pedido; 0 notas para pedidos
  cujo pagamento falhou.
- **SC-002**: 100% das notas com valor acima do limite resultam em `InvoiceFailed` e 100% das com
  valor dentro do limite resultam em `InvoiceIssued`, de forma repetível.
- **SC-003**: Reentregar N vezes o mesmo evento de pagamento resulta em 0 notas extras e 0 eventos
  de saída extras.
- **SC-004**: Com o broker indisponível durante N pedidos, 0 pedidos são perdidos: após a
  recuperação, 100% dos resultados são entregues e o serviço permanece saudável. Com o banco
  indisponível durante N eventos, 0 são descartados.
- **SC-005**: Após interrupção forçada entre persistir e publicar, 100% dos eventos pendentes são
  entregues depois do restart.
- **SC-006**: Para qualquer pedido, 100% das observações dos eventos de resultado respeitam a
  ordem das decisões, e 100% dos eventos publicados passam na conferência de conformidade com o
  contrato do E2.1.
- **SC-007**: Um operador consegue, só pelos logs, localizar o processamento e a publicação de
  qualquer pedido (`orderId`/`eventId`) e identificar qualquer falha de entrega.
- **SC-008**: Os contratos HTTP do `invoice-api` permanecem idênticos e a instrumentação básica do
  item 1.12 continua funcionando.
- **SC-009**: Zero dependências de código novas compartilhadas entre os 3 serviços e zero
  alterações no `order-api` e no `payment-api`.

## Assumptions

- Esta feature é o item E2.4 do ROADMAP (Fase 2); consome o contrato do E2.1 e os eventos do E2.3
  sem reabri-los, e reaproveita as decisões de produtor/consumidor validadas no E2.2/E2.3 (outbox
  transacional com relay, idempotência por restrições únicas, política de erro de consumo).
- Regra de decisão (padrão): a nota falha quando o valor é **estritamente maior que 500.00**;
  caso contrário é emitida. O limite é configurável. O motivo de falha usa um código estável
  (ex.: `AMOUNT_ABOVE_ISSUANCE_LIMIT`). O limite é menor que o do pagamento (1000.00) de
  propósito: pedidos entre 500.00 e 1000.00 têm pagamento reservado e nota que falha, exercitando
  o caminho de falha da nota ponta a ponta sem alterar outros serviços.
- Uma nota que falhou é persistida (com a situação "falhou") para rastreabilidade; ela não passa
  por `issue`/`cancel`. Uma nota emitida a partir de evento nasce já emitida.
- O mecanismo exato de consistência e de deduplicação fica para o `/speckit-plan`; esta spec
  define só as garantias.
- Entrega pelo menos uma vez, deduplicação pelo `eventId` a cargo do consumidor; ordem garantida
  por pedido, não globalmente.
- O consumidor lê `payment.reserved` desde o início na primeira execução.
- Kafka e variáveis de bootstrap do `invoice-api` já existem em Compose e k8s; espera-se pouca ou
  nenhuma mudança de infraestrutura.
- O `invoice-api` já persiste em Postgres via JPA, tem logging estruturado com `traceId`/`spanId`
  e a dependência do starter Kafka; pré-requisitos atendidos.
