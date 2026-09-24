# Feature Specification: Validação de Request (Bean Validation)

**Feature Branch**: `004-request-validation`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Validação de request (Bean Validation) nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje os DTOs de criação (OrderRequest, PaymentReservationRequest, InvoiceRequest) não têm nenhuma anotação de validação, spring-boot-starter-validation nem está no pom.xml, e os controllers não usam @Valid — então um POST com customerId nulo, amount negativo, ou orderId/paymentId em qualquer formato de texto é aceito e cria o recurso normalmente. Confirmei que spring-boot-starter-validation:4.1.1 resolve corretamente no BOM do projeto (Spring Boot 4.1.1). O objetivo é: adicionar spring-boot-starter-validation aos 3 pom.xml; anotar os campos obrigatórios dos 3 DTOs (customerId/orderId/paymentId como @NotBlank; amount como @NotNull @Positive); adicionar @Valid nos métodos de criação dos 3 controllers; e decidir o formato de orderId/paymentId em PaymentReservationRequest/InvoiceRequest (hoje String livre, sem validar UUID) — a decisão é mantê-los como String mas validar o formato UUID via @Pattern, sem mudar o tipo do campo pros Models/Responses (isso é fora de escopo, ligado à Fase de persistência). Erros de validação devem retornar 400 com um corpo que identifica quais campos falharam e por quê (estendendo o ErrorResponse já criado no item 1.3, adicionando uma lista opcional de erros de validação, sem quebrar o formato usado pelo 404). Este é o item 1.4 do ROADMAP.md da Fase 1, e depende dos itens 1.1, 1.2 e 1.3 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Requisição inválida é rejeitada com 400 (Priority: P1)

Como consumidor de qualquer uma das 3 APIs, quero que uma requisição de criação com dados
obrigatórios ausentes ou inválidos (ex.: `customerId` vazio, `amount` negativo) seja
rejeitada com `400 Bad Request`, em vez de criar um recurso inconsistente.

**Why this priority**: é o problema central desta feature — hoje qualquer dado é aceito
silenciosamente, criando registros que não deveriam existir (pedido sem cliente, valor
negativo, etc.).

**Independent Test**: enviar `POST` com `customerId` ausente/vazio ou `amount` negativo/zero
para cada um dos 3 serviços e observar status `400`.

**Acceptance Scenarios**:

1. **Given** um `POST /api/orders` com `customerId` ausente ou vazio, **When** a requisição é
   enviada, **Then** a resposta tem status `400` e o pedido não é criado.
2. **Given** um `POST /api/orders`/`/api/payments`/`/api/invoices` com `amount` ausente, zero
   ou negativo, **When** a requisição é enviada, **Then** a resposta tem status `400` e o
   recurso não é criado.
3. **Given** uma requisição com todos os campos obrigatórios presentes e válidos, **When**
   enviada a qualquer um dos 3 serviços, **Then** o comportamento de sucesso (`201`)
   permanece exatamente como é hoje — sem regressão.

---

### User Story 2 - Formato de referência entre serviços é validado (Priority: P2)

Como consumidor de `payment-api`/`invoice-api`, quero que `orderId` (e `paymentId`, em
`invoice-api`) sejam rejeitados quando não estiverem no formato esperado (UUID), para
detectar erros de integração cedo, em vez de descobrir depois que a referência nunca vai
casar com nada.

**Why this priority**: menos crítico que a validação básica de campos obrigatórios (P1), mas
evita que referências obviamente quebradas (texto livre em vez de um id) sejam aceitas.

**Independent Test**: enviar `orderId`/`paymentId` com um valor que não é um UUID (ex.:
`"abc"`) e observar `400`.

**Acceptance Scenarios**:

1. **Given** um `POST /api/payments` com `orderId` que não está no formato UUID, **When** a
   requisição é enviada, **Then** a resposta tem status `400`.
2. **Given** um `POST /api/invoices` com `orderId` ou `paymentId` que não está no formato
   UUID, **When** a requisição é enviada, **Then** a resposta tem status `400`.
3. **Given** um `orderId`/`paymentId` no formato UUID válido (mesmo que não corresponda a um
   recurso que realmente existe em outro serviço), **When** a requisição é enviada, **Then**
   ela passa nesta validação — checar a *existência* real do recurso referenciado está fora
   do escopo desta feature (ver Edge Cases).

---

### User Story 3 - Resposta de erro identifica todos os campos inválidos (Priority: P3)

Como consumidor de uma API, quero que a resposta `400` liste todos os campos que falharam a
validação (não só o primeiro), para corrigir minha requisição em uma única tentativa.

**Why this priority**: melhora a experiência de quem integra com a API, mas depende que P1/P2
já estejam implementados.

**Independent Test**: enviar uma requisição com 2+ campos inválidos simultaneamente e
inspecionar o corpo da resposta `400`.

**Acceptance Scenarios**:

1. **Given** uma requisição com `customerId` vazio **e** `amount` negativo ao mesmo tempo,
   **When** enviada, **Then** o corpo da resposta `400` lista ambos os problemas, não apenas
   um.

---

### Edge Cases

- Um `orderId`/`paymentId` sintaticamente válido (é um UUID) mas que não corresponde a
  nenhum recurso real em outro serviço — esta feature valida só o **formato**, não a
  **existência**; confirmar que o recurso referenciado existe de fato é uma integração
  cross-service (ex.: via evento Kafka), fora de escopo até a Fase 2.
- Corpo da requisição que nem é um JSON válido (sintaxe malformada) — já é tratado pelo
  Spring por padrão como `400`, antes mesmo da validação Bean Validation rodar; não deve
  regredir.
- Requisições de ação sobre um recurso já existente (`/confirm`, `/cancel`, `/issue`, que não
  recebem corpo) não são afetadas por esta feature — só os endpoints de criação (`POST` com
  corpo) são validados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Os 3 serviços MUST validar o corpo da requisição nos respectivos endpoints de
  criação (`POST /api/orders`, `POST /api/payments`, `POST /api/invoices`), rejeitando com
  `400` quando um campo obrigatório estiver ausente ou inválido.
- **FR-002**: Em `order-api`, `customerId` MUST ser obrigatório e não-vazio; `amount` MUST
  ser obrigatório e maior que zero.
- **FR-003**: Em `payment-api`, `orderId` MUST ser obrigatório, não-vazio e MUST estar no
  formato UUID; `amount` MUST ser obrigatório e maior que zero.
- **FR-004**: Em `invoice-api`, `orderId` e `paymentId` MUST ser obrigatórios, não-vazios e
  MUST estar no formato UUID; `amount` MUST ser obrigatório e maior que zero.
- **FR-005**: Quando a validação falhar, a resposta `400` MUST identificar todos os campos
  inválidos e o motivo de cada um, não apenas o primeiro encontrado.
- **FR-006**: A validação MUST ser implementada de forma independente em cada um dos 3
  serviços (Princípio I da constitution) — mecanismo de validação padrão (Bean Validation),
  sem biblioteca de validação compartilhada entre eles.
- **FR-007**: Requisições que já são aceitas hoje com dados completos e válidos MUST
  continuar sendo aceitas exatamente como são, sem mudança no comportamento de sucesso.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das requisições de criação com campo obrigatório ausente, vazio, ou
  numericamente inválido (`amount` ≤ 0) retornam `400` nos 3 serviços (baseline atual: 100%
  são aceitas e criam o recurso mesmo assim).
- **SC-002**: 100% das requisições com `orderId`/`paymentId` fora do formato UUID retornam
  `400` em `payment-api`/`invoice-api`.
- **SC-003**: O corpo de toda resposta `400` desta feature lista pelo menos um problema por
  campo inválido presente na requisição — uma requisição com 2 campos inválidos produz uma
  resposta que menciona os 2, não só 1.
- **SC-004**: Nenhuma regressão — toda requisição de criação com dados completos e válidos,
  incluindo os cenários já cobertos pela suíte de testes existente, continua retornando `201`
  com exatamente o mesmo corpo de resposta de hoje.

## Assumptions

- `spring-boot-starter-validation:4.1.1` foi confirmado como um artifact válido, que resolve
  no BOM do Spring Boot 4.1.1 já usado pelo projeto (verificado antes desta spec).
- `orderId`/`paymentId` em `PaymentReservationRequest`/`InvoiceRequest` permanecem com tipo
  `String` (não migram para `UUID`) — a validação de formato é feita via expressão regular
  compatível com UUID. Migrar o tipo do campo ao longo de todo o fluxo (Model, Response) é
  uma mudança maior, associada à introdução de persistência real (itens 1.9/1.10 do
  ROADMAP), e fica fora do escopo desta feature.
- O corpo de erro de validação estende o `ErrorResponse` já criado no item 1.3 (adicionando
  uma lista opcional de erros de validação), em vez de introduzir um formato de erro
  paralelo — mantém um único contrato de erro por serviço.
- Validar a *existência* real de um `orderId`/`paymentId` referenciado (chamando outro
  serviço) está fora de escopo — depende de comunicação entre serviços, que só chega na
  Fase 2 (Kafka).
