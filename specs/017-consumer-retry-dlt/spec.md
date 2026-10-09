# Feature Specification: Estratégia de Erro de Consumo (Retry Limitado e Dead-Letter Topic)

**Feature Branch**: `017-consumer-retry-dlt`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "E2.5 — Estratégia de erro de consumo: retry limitado + dead-letter topic (DLT) por serviço consumidor, para payment-api (consome order.created) e invoice-api (consome payment.reserved), cada um com a sua implementação (Princípio I). Hoje (E2.3/E2.4) falha transitória é repetida a cada 1 s sem limite (bloqueia a partição) e mensagem inválida é só logada e pulada (some do sistema). Esta feature: retry limitado com espera crescente para falha transitória, com a mensagem estacionada no DLT ao esgotar as tentativas (nunca descartada) e o consumo seguinte continuando; mensagem permanentemente inválida vai direto ao DLT; o DLT preserva a mensagem original e o contexto de diagnóstico; convenção `<tópico-de-origem>.dlt` registrada no contrato do E2.1; DLT indisponível não perde a mensagem; observabilidade de cada retry/envio ao DLT; parâmetros configuráveis; procedimento documentado e validado de reprocessamento manual do DLT. Mantém as garantias do E2.3/E2.4. Fora de escopo: Testcontainers Kafka (E2.6), propagação de trace por header (E6.4), UI/endpoint de gestão do DLT, DLT para o relay do outbox, mudanças no order-api."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Falha transitória prolongada não trava o consumo nem perde mensagens (Priority: P1)

Como desenvolvedor do laboratório, quero que, quando o processamento de uma mensagem falha por
um motivo transitório (ex.: banco indisponível), o serviço tente de novo algumas vezes com espera
crescente e, se a falha persistir, **estacione** a mensagem num tópico de mensagens mortas e siga
para as próximas — em vez de repetir para sempre travando a partição (comportamento atual) —
para que uma mensagem ou uma queda longa não paralise o fluxo, e nada se perca.

**Why this priority**: é o risco que os itens E2.3 e E2.4 deixaram explícito ("bloqueio na cabeça
da partição"). Sem limite, uma falha longa bloqueia todos os pedidos daquela partição.

**Independent Test**: com o serviço consumindo, derrubar o banco por mais tempo que o limite de
tentativas, enviar mensagens, religar o banco e verificar que as mensagens ficaram no DLT com a
mensagem original intacta, que o serviço voltou a processar as mensagens seguintes e que nenhuma
mensagem sumiu.

**Acceptance Scenarios**:

1. **Given** uma falha transitória que se resolve antes de esgotar as tentativas, **When** a
   mensagem é reprocessada, **Then** ela é processada normalmente, nada vai ao DLT e as esperas
   entre tentativas crescem a cada nova tentativa.
2. **Given** uma falha transitória que persiste além do limite de tentativas, **When** as
   tentativas se esgotam, **Then** a mensagem é publicada no DLT do tópico de origem e o consumo
   continua com a mensagem seguinte.
3. **Given** várias mensagens chegando durante uma falha longa, **When** cada uma esgota suas
   tentativas, **Then** todas são estacionadas no DLT (nenhuma é descartada) e o tempo total até o
   consumo normal voltar é limitado pela política de retry, não indefinido.
4. **Given** o DLT também indisponível no momento de estacionar, **When** o envio ao DLT falha,
   **Then** a mensagem não é perdida: ela é reprocessada de novo depois, até conseguir ser
   estacionada ou processada.

---

### User Story 2 - Mensagem permanentemente inválida é estacionada, não apenas logada (Priority: P2)

Como desenvolvedor do laboratório, quero que uma mensagem que nunca poderá ser processada (JSON
ilegível, campo obrigatório ausente ou inválido) vá direto ao DLT, sem tentativas inúteis, em vez
de só aparecer num log e sumir, para que eu possa inspecionar, corrigir a causa e reprocessar.

**Why this priority**: hoje (E2.3/E2.4) a mensagem inválida é descartada com um log — o conteúdo
se perde para sempre. Depende de US1 (a infraestrutura do DLT), mas é o que torna o descarte
recuperável.

**Independent Test**: publicar no tópico de entrada uma mensagem ilegível e outra com campo
obrigatório ausente, seguidas de uma válida; verificar que as duas inválidas aparecem no DLT sem
retries e que a válida é processada normalmente.

**Acceptance Scenarios**:

1. **Given** uma mensagem de entrada ilegível ou incompleta, **When** o serviço a consome,
   **Then** ela é publicada no DLT imediatamente (sem tentativas de retry) e as mensagens
   seguintes continuam sendo processadas.
2. **Given** uma mensagem estacionada no DLT, **When** ela é inspecionada, **Then** a chave e o
   valor originais estão intactos e há informação de diagnóstico: tópico, partição e offset de
   origem e a causa da falha.

---

### User Story 3 - Mensagens do DLT podem ser reprocessadas sem duplicar resultados (Priority: P3)

Como desenvolvedor do laboratório, quero um procedimento documentado e comprovado para devolver
uma mensagem do DLT ao tópico de origem depois de corrigir a causa, e a garantia de que o
reprocessamento converge para o mesmo estado (sem pagamento/nota duplicados), para que o DLT seja
um estacionamento seguro e não um beco sem saída.

**Why this priority**: fecha o ciclo operacional do DLT, mas depende de US1/US2 já existirem.

**Independent Test**: com mensagens no DLT, seguir o procedimento documentado para reenviá-las ao
tópico de origem e verificar que cada uma vira exatamente um pagamento/nota e um evento de saída,
mesmo se uma delas já tinha sido processada antes.

**Acceptance Scenarios**:

1. **Given** uma mensagem válida estacionada no DLT por falha transitória e a causa já corrigida,
   **When** ela é reenviada ao tópico de origem pelo procedimento documentado, **Then** é
   processada uma vez, gerando um único resultado.
2. **Given** uma mensagem que já havia sido processada antes e que é reenviada do DLT, **When** o
   serviço a consome, **Then** a duplicata é ignorada (idempotência) e nenhum resultado novo
   aparece.

---

### Edge Cases

- **Falha transitória que se resolve na 2ª tentativa**: processada normalmente; nada no DLT.
- **Falha que dura mais que o limite**: a mensagem vai ao DLT; as seguintes também, enquanto durar
  a falha; ao cessar, o consumo normal retoma sozinho (as já estacionadas ficam no DLT).
- **DLT indisponível** (ex.: broker fora): a mensagem não é perdida; é reprocessada depois
  (FR-007).
- **Mensagem inválida com `eventId`/`orderId` ilegíveis**: vai ao DLT mesmo assim; o log registra
  o que for legível e o contexto de origem (tópico, partição, offset).
- **Mensagem sem valor (nula)**: tratada como inválida e estacionada.
- **Reprocessar do DLT algo que continua inválido**: volta ao DLT (sem loop infinito além do
  limite de tentativas).
- **Ordem por pedido**: ao estacionar uma mensagem no DLT, as seguintes seguem; a ordem entre uma
  mensagem estacionada e as processadas depois não é garantida — documentado como efeito
  conhecido do retry limitado.
- **Idempotência**: toda mensagem processada antes (reentrega/reprocessamento) continua ignorada
  como duplicata (garantia do E2.3/E2.4 preservada).
- **Crescimento do DLT**: o DLT é retido pelo broker conforme a retenção padrão; não há limpeza
  própria nesta feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada serviço consumidor (`payment-api` em `order.created` e `invoice-api` em
  `payment.reserved`) MUST tratar falha transitória de processamento com retry **limitado**, com
  espera crescente entre tentativas (espera inicial, multiplicador e espera máxima), em vez do
  retry sem limite atual. Esgotadas as tentativas, a mensagem MUST ser estacionada no DLT e o
  consumo MUST seguir para a mensagem seguinte.
- **FR-002**: Uma mensagem permanentemente inválida (ilegível, campo obrigatório ausente ou
  inválido, valor nulo) MUST ir direto ao DLT, sem tentativas de retry, em vez de ser apenas
  logada e descartada.
- **FR-003**: Nenhuma mensagem MUST ser descartada sem ser processada ou estacionada no DLT: o
  único destino de uma mensagem que não é processada com sucesso é o DLT.
- **FR-004**: A mensagem estacionada no DLT MUST preservar a chave e o valor originais e MUST
  carregar o contexto de diagnóstico: tópico, partição e offset de origem e a causa da falha.
- **FR-005**: O nome do DLT MUST seguir a convenção `<tópico-de-origem>.dlt` em minúsculas
  (`order.created.dlt`, `payment.reserved.dlt`); o DLT MUST ser declarado explicitamente pelo
  serviço consumidor com 3 partições e 1 réplica, e a convenção MUST ser registrada como
  acréscimo ao contrato do E2.1 (`specs/013-kafka-event-convention/contracts/event-contract.md`).
- **FR-006**: A mensagem MUST ser publicada no DLT preservando a mesma chave da original, de modo
  que mensagens do mesmo pedido fiquem na mesma partição do DLT.
- **FR-007**: Se o envio ao DLT falhar (ex.: DLT ou broker indisponível), a mensagem MUST NOT ser
  perdida: ela MUST ser reprocessada depois até ser processada ou estacionada.
- **FR-008**: Cada retry e cada envio ao DLT MUST ser observável por log estruturado contendo, quando
  legíveis, `orderId` e `eventId`, além de tópico, partição, offset, número da tentativa e causa,
  correlacionado com `traceId`/`spanId` (Princípio VI).
- **FR-009**: Os parâmetros do retry (número de tentativas, espera inicial, multiplicador e espera
  máxima) MUST ser configuráveis por serviço, com padrões razoáveis que mantenham o tempo total
  de retry de uma mensagem em poucas dezenas de segundos.
- **FR-010**: MUST existir um procedimento documentado e validado para reprocessar manualmente
  mensagens do DLT de volta ao tópico de origem, sem criar endpoint nem ferramenta nova; o
  reprocessamento MUST convergir sem duplicar resultados (idempotência por `eventId`, FR-011).
- **FR-011**: As garantias do E2.3/E2.4 MUST ser preservadas: idempotência por `eventId` e no
  máximo um pagamento/nota por pedido originado de evento, atomicidade entre a decisão e o evento
  de saída, ordem por pedido nas mensagens processadas, eventos de saída e contratos HTTP
  inalterados.
- **FR-012**: A implementação MUST ser independente em cada serviço (Princípio I): nenhuma
  dependência de código compartilhada entre `payment-api` e `invoice-api`, e nenhuma alteração no
  `order-api`.
- **FR-013**: A configuração MUST funcionar de forma consistente no Docker Compose e no cluster
  k8s local (Princípio IV), e o comportamento MUST ser validado empiricamente contra Kafka e
  Postgres reais (banco parado além do limite de retry, mensagem ilegível, reprocessamento do DLT).
- **FR-014**: A suíte automatizada de cada serviço MUST cobrir a classificação de falhas
  (transitória × permanente), o limite e o crescimento das esperas, o destino ao DLT com chave/
  valor/contexto preservados e a não-perda quando o envio ao DLT falha, e MUST continuar
  autossuficiente (só exige um runtime de contêiner).
- **FR-015**: Esta feature MUST NOT implementar a suíte Testcontainers Kafka (E2.6), a propagação
  de contexto de trace por headers Kafka (E6.4), endpoint/UI de gestão do DLT, nem DLT para o relay
  do outbox (que mantém seu próprio retry), e MUST NOT alterar o `order-api`.

### Key Entities *(include if feature involves data)*

- **Mensagem consumida**: evento de entrada (`OrderCreated` no `payment-api`, `PaymentReserved` no
  `invoice-api`) com chave e valor; pode ser processada, estacionada ou reprocessada.
- **Mensagem morta**: cópia da mensagem original no DLT, com chave e valor intactos e contexto de
  diagnóstico (origem e causa). Existe só como estacionamento; não tem estado próprio.
- **Política de retry**: parâmetros que definem quantas vezes e com que esperas uma falha
  transitória é repetida antes de estacionar a mensagem.
- **Pagamento/Nota decididos**: continuam sendo o marcador de "evento já tratado" (idempotência do
  E2.3/E2.4); a reentrega do DLT depende disso para não duplicar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com o banco indisponível além do limite de retry, 100% das mensagens recebidas nesse
  período terminam no DLT (0 perdidas) e, depois que o banco volta, as mensagens seguintes são
  processadas normalmente sem intervenção.
- **SC-002**: O tempo máximo que uma única mensagem com falha transitória retém o consumo de uma
  partição é limitado pela política de retry (padrão: no máximo cerca de 30 segundos), nunca
  indefinido.
- **SC-003**: 100% das mensagens de entrada permanentemente inválidas aparecem no DLT sem
  tentativas de retry, com chave e valor idênticos aos originais e com tópico, partição, offset e
  causa disponíveis.
- **SC-004**: 0 mensagens são descartadas sem processamento ou estacionamento, inclusive quando o
  DLT está indisponível no momento do envio.
- **SC-005**: Reprocessar N mensagens do DLT pelo procedimento documentado resulta em exatamente
  um resultado (pagamento/nota e evento de saída) por pedido, incluindo mensagens que já haviam
  sido processadas antes (0 duplicatas).
- **SC-006**: Um operador consegue, só pelos logs, identificar cada retry e cada mensagem enviada
  ao DLT (pedido, evento, origem, tentativa e causa).
- **SC-007**: Os contratos HTTP e os eventos de saída dos dois serviços permanecem idênticos, e a
  suíte de testes dos dois serviços continua verde sem alterar testes de contrato existentes.
- **SC-008**: Zero dependências de código compartilhadas entre os serviços e zero alterações no
  `order-api`.

## Assumptions

- Esta feature é o item E2.5 do ROADMAP (Fase 2); ela **substitui** a política interina do E2.3/
  E2.4 (retry sem limite para falha transitória; log e descarte para mensagem inválida), que as
  specs 015 e 016 já registravam como provisória.
- Padrões razoáveis do retry: 4 retentativas (5 tentativas no total) com espera inicial de 1 s,
  multiplicador 2 e espera máxima de 10 s (cerca de 15 s no total). Tudo configurável.
- Falha **transitória** = qualquer erro de processamento que não seja de validação do conteúdo
  (ex.: indisponibilidade do banco); falha **permanente** = conteúdo ilegível, campo obrigatório
  ausente/inválido ou valor nulo. Na dúvida, uma falha desconhecida é tratada como transitória
  (tenta, e ao fim vai ao DLT — nunca é descartada).
- O DLT é um tópico por tópico de origem (`order.created.dlt`, `payment.reserved.dlt`); como cada
  tópico de origem hoje tem um único serviço consumidor, o DLT é de fato "por serviço". Se um
  tópico passar a ter mais de um consumidor, a convenção será revisitada.
- O reprocessamento do DLT é manual e documentado (reenvio ao tópico de origem com as ferramentas
  de linha de comando do Kafka); sem endpoint, UI ou ferramenta nova.
- Aceita-se que, ao estacionar uma mensagem no DLT, a ordem por pedido entre ela e as mensagens
  processadas depois não é garantida (efeito do retry limitado); a idempotência garante que o
  reprocessamento não duplica resultados.
- Retenção e limpeza do DLT seguem o padrão do broker; fora de escopo.
- Kafka, Postgres e as variáveis de bootstrap dos serviços já existem em Compose e k8s; espera-se
  pouca ou nenhuma mudança de infraestrutura.
