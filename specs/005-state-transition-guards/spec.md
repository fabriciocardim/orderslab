# Feature Specification: Máquina de Estados / Guardas de Transição

**Feature Branch**: `005-state-transition-guards`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Máquina de estados / guardas de transição nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje nenhum serviço valida o estado atual antes de aplicar uma transição: dá pra confirmar um pedido já cancelado, cancelar um pagamento já confirmado, emitir uma nota já cancelada, etc. — o código simplesmente sobrescreve o status sem checar nada. O objetivo é bloquear transições ilegais retornando 409 Conflict (reaproveitando o ErrorResponse já criado nos itens 1.3/1.4), com uma máquina de estados simples e independente por serviço: cada recurso tem um único estado inicial acionável (order-api: PENDING; payment-api: RESERVED; invoice-api: PENDING), a partir do qual só uma transição para CONFIRMED/CANCELLED (ou ISSUED/CANCELLED em invoice-api) é permitida — os dois estados finais são terminais, sem transição de volta. Decisão já tomada sobre os valores de enum mortos: remover OrderStatus.COMPLETED (sem gatilho síncrono possível antes da Fase 2/Kafka), remover PaymentStatus.FAILED e InvoiceStatus.FAILED (sem regra de negócio simulada nesta feature — fica para decisão futura). Achado adicional durante a investigação: PaymentStatus.PENDING também é morto — o construtor de Payment cria com PENDING mas reserve() sempre sobrescreve pra RESERVED antes de qualquer resposta, então PENDING nunca é observável; aplicar o mesmo critério e remover, simplificando o construtor de Payment para criar diretamente como RESERVED. Este é o item 1.5 do ROADMAP.md da Fase 1, e depende dos itens 1.1-1.4 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Transições ilegais são bloqueadas (Priority: P1)

Como consumidor de qualquer uma das 3 APIs, quero que uma tentativa de confirmar/cancelar
(ou emitir) um recurso que já saiu do seu estado inicial seja rejeitada com `409 Conflict`,
em vez de sobrescrever o status silenciosamente.

**Why this priority**: é o problema central desta feature — hoje é possível, por exemplo,
"confirmar" um pedido já cancelado, deixando o sistema num estado inconsistente sem nenhum
aviso.

**Independent Test**: criar um recurso, aplicar uma transição válida (ex.: cancelar), depois
tentar aplicar outra transição sobre o mesmo recurso (ex.: confirmar) e observar `409`.

**Acceptance Scenarios**:

1. **Given** um pedido `CANCELLED`, **When** `POST /api/orders/{id}/confirm` é chamado,
   **Then** a resposta tem status `409` e o pedido permanece `CANCELLED`.
2. **Given** um pedido `CONFIRMED`, **When** `POST /api/orders/{id}/cancel` é chamado,
   **Then** a resposta tem status `409` e o pedido permanece `CONFIRMED`.
3. **Given** um pagamento `CANCELLED`, **When** `POST /api/payments/{id}/confirm` é chamado,
   **Then** a resposta tem status `409`.
4. **Given** uma nota fiscal `CANCELLED`, **When** `POST /api/invoices/{id}/issue` é
   chamado, **Then** a resposta tem status `409`.
5. **Given** um recurso recém-criado no seu estado inicial (pedido/nota `PENDING`, pagamento
   `RESERVED`), **When** uma transição válida é aplicada pela primeira vez, **Then** ela
   continua funcionando exatamente como hoje (sem regressão).

---

### User Story 2 - Estados sem uso real são removidos (Priority: P2)

Como desenvolvedor consumindo qualquer uma das 3 APIs, quero que só apareçam nas respostas
estados que o sistema realmente é capaz de produzir hoje, para não ter que tratar
"fantasmas" (`COMPLETED`, `FAILED`, um `PENDING` que nunca aparece) que nunca vêm de
verdade.

**Why this priority**: menos crítico que bloquear transições ilegais (P1), mas evita
documentação e código de cliente escritos para estados que não existem na prática.

**Independent Test**: inspecionar os 3 enums de status e confirmar que cada valor presente
é alcançável por algum caminho real do sistema hoje.

**Acceptance Scenarios**:

1. **Given** o enum `OrderStatus`, **When** inspecionado, **Then** contém apenas `PENDING`,
   `CONFIRMED`, `CANCELLED` — sem `COMPLETED`.
2. **Given** o enum `PaymentStatus`, **When** inspecionado, **Then** contém apenas
   `RESERVED`, `CONFIRMED`, `CANCELLED` — sem `PENDING` nem `FAILED`.
3. **Given** o enum `InvoiceStatus`, **When** inspecionado, **Then** contém apenas
   `PENDING`, `ISSUED`, `CANCELLED` — sem `FAILED`.
4. **Given** um pagamento recém-criado via `POST /api/payments`, **When** a resposta é
   inspecionada, **Then** o status retornado já é `RESERVED` (nunca `PENDING`).

---

### Edge Cases

- Chamadas concorrentes de confirm/cancel sobre o mesmo recurso (ex.: duas requisições
  simultâneas) podem, em tese, ambas lerem o mesmo estado inicial antes de qualquer uma
  escrever — uma condição de corrida teórica do armazenamento em memória atual
  (`ConcurrentHashMap` sem lock de transição). Aceito como limitação conhecida desta fase;
  será revisitado quando a persistência real (itens 1.9/1.10) trouxer controle
  transacional.
- Endpoints de criação (`POST /api/orders`, `/api/payments`, `/api/invoices`) e de leitura
  (`GET`) não são afetados por esta feature — as guardas só se aplicam às transições
  (`confirm`/`cancel`/`issue`).
- Um id inexistente continua retornando `404` (item 1.3), não `409` — a guarda de transição
  só entra em jogo depois que o recurso é encontrado.
- Reintroduzir `OrderStatus.COMPLETED` com um gatilho real fica para quando a Fase 2 (Kafka)
  permitir `order-api` reagir a pagamento e nota fiscal concluídos. Reintroduzir um estado de
  falha simulado (`FAILED`) fica para uma decisão de negócio futura e explícita — fora do
  escopo desta feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `order-api` `confirm()` MUST só ter sucesso quando o pedido estiver `PENDING`;
  caso contrário MUST retornar `409`.
- **FR-002**: `order-api` `cancel()` MUST só ter sucesso quando o pedido estiver `PENDING`;
  caso contrário MUST retornar `409`.
- **FR-003**: `payment-api` `confirm()` MUST só ter sucesso quando o pagamento estiver
  `RESERVED`; caso contrário MUST retornar `409`.
- **FR-004**: `payment-api` `cancel()` MUST só ter sucesso quando o pagamento estiver
  `RESERVED`; caso contrário MUST retornar `409`.
- **FR-005**: `invoice-api` `issue()` MUST só ter sucesso quando a nota estiver `PENDING`;
  caso contrário MUST retornar `409`.
- **FR-006**: `invoice-api` `cancel()` MUST só ter sucesso quando a nota estiver `PENDING`;
  caso contrário MUST retornar `409`.
- **FR-007**: A resposta `409` MUST reaproveitar o `ErrorResponse` já estabelecido (itens
  1.3/1.4), com `status=409`, `error="Conflict"`, e `message` descrevendo o estado atual e a
  transição rejeitada.
- **FR-008**: `OrderStatus.COMPLETED` MUST ser removido do enum.
- **FR-009**: `PaymentStatus.FAILED` e `InvoiceStatus.FAILED` MUST ser removidos dos
  respectivos enums.
- **FR-010**: `PaymentStatus.PENDING` MUST ser removido; o construtor de `Payment` MUST
  criar o registro já como `RESERVED`, refletindo que "reservar" já é a própria ação de
  criação neste domínio.
- **FR-011**: A guarda de transição MUST ser implementada de forma independente em cada um
  dos 3 serviços (Princípio I da constitution) — sem biblioteca de máquina de estados
  compartilhada.
- **FR-012**: Endpoints de criação (`POST` inicial) e de leitura (`GET`) MUST continuar
  funcionando exatamente como hoje, não afetados pelas guardas de transição.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das tentativas de aplicar `confirm`/`cancel`/`issue` sobre um recurso que
  já não está no seu estado inicial acionável retornam `409` nos 3 serviços (baseline atual:
  100% são aceitas e sobrescrevem o status).
- **SC-002**: 100% das transições válidas (a partir do estado inicial correto, aplicadas uma
  única vez) continuam funcionando exatamente como hoje.
- **SC-003**: Nenhum dos 4 valores de enum sem uso real identificados
  (`OrderStatus.COMPLETED`, `PaymentStatus.PENDING`, `PaymentStatus.FAILED`,
  `InvoiceStatus.FAILED`) permanece em qualquer um dos 3 serviços após esta feature.
- **SC-004**: Nenhuma regressão nos endpoints de criação e leitura dos 3 serviços.

## Assumptions

- Controle de concorrência real (locks/transações) para a guarda de transição fica fora de
  escopo — é uma limitação já conhecida do armazenamento em memória atual, endereçada quando
  a persistência real (itens 1.9/1.10) for implementada.
- Reintroduzir `OrderStatus.COMPLETED` (Fase 2, via evento Kafka) ou algum estado de falha
  simulado (`FAILED`, com uma regra de negócio explícita) são decisões de features futuras,
  não desta.
- O texto de `message` na resposta `409` é livre (não padronizado por um formato específico),
  desde que identifique claramente o estado atual e a ação rejeitada — consistente com como
  `message` já é usado nas respostas `404`/`400` existentes.
