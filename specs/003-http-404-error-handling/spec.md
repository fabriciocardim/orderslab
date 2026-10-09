# Feature Specification: Tratamento de Erro HTTP — 404 Real

**Feature Branch**: `003-http-404-error-handling`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Tratamento de erro HTTP correto nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje, quando um id inexistente é buscado/confirmado/cancelado, o serviço lança OrderNotFoundException/PaymentNotFoundException/InvoiceNotFoundException (uma RuntimeException simples, sem @ResponseStatus) e, como não existe nenhum @ControllerAdvice ou @ExceptionHandler em nenhum dos 3 serviços, a exceção não é tratada e o Spring Boot retorna o handler de erro padrão com status 500, em vez de 404. O objetivo é adicionar, em cada um dos 3 serviços de forma independente (um @RestControllerAdvice por serviço, sem biblioteca compartilhada entre eles), o mapeamento da respectiva *NotFoundException para HTTP 404, com um corpo de resposta de erro consistente (ex.: timestamp, status, error, message, path). Este é o item 1.3 do ROADMAP.md da Fase 1, e depende dos itens 1.1 e 1.2 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Status HTTP correto para recurso inexistente (Priority: P1)

Como consumidor de qualquer uma das 3 APIs, quero receber `404 Not Found` (não `500`)
quando eu operar sobre um id que não existe, para conseguir distinguir "eu errei o id" de
"o servidor quebrou".

**Why this priority**: é o problema central desta feature — hoje todo erro de "não
encontrado" aparece como falha interna do servidor, o que é enganoso e dificulta debugging
tanto para o cliente quanto para quem opera o serviço.

**Independent Test**: chamar `GET /api/orders/{id-inexistente}` (e os equivalentes em
`payment-api`/`invoice-api`) e observar status `404`.

**Acceptance Scenarios**:

1. **Given** um id que não existe em nenhum dos 3 serviços, **When** `GET /api/{recurso}/{id}`
   é chamado, **Then** a resposta tem status `404`.
2. **Given** um id que não existe, **When** `POST /api/orders/{id}/confirm` (ou
   `/cancel`) é chamado em `order-api`/`payment-api`, ou `POST /api/invoices/{id}/issue`
   (ou `/cancel`) em `invoice-api`, **Then** a resposta tem status `404`.
3. **Given** um id que existe, **When** qualquer um dos endpoints acima é chamado,
   **Then** o comportamento de sucesso (200/201) permanece exatamente como é hoje — sem
   regressão.

---

### User Story 2 - Corpo de erro estruturado e consistente (Priority: P2)

Como consumidor de uma API, quero que a resposta `404` tenha um corpo estruturado (não uma
página de erro HTML genérica), para conseguir tratar o erro programaticamente no meu
cliente.

**Why this priority**: menos crítico que corrigir o status em si (P1), mas sem isso o
cliente ainda precisa adivinhar o motivo do erro a partir de texto livre.

**Independent Test**: inspecionar o corpo JSON de uma resposta `404` e confirmar a presença
dos campos esperados.

**Acceptance Scenarios**:

1. **Given** uma resposta `404` de qualquer um dos 3 serviços, **When** o corpo é
   inspecionado, **Then** ele contém, no mínimo, os campos `timestamp`, `status`, `error`,
   `message` e `path`.
2. **Given** o campo `message`, **When** inspecionado, **Then** contém a mesma informação já
   produzida hoje pela exceção (ex.: "Pedido não encontrado: `<id>`"), sem informação nova
   exposta.

---

### User Story 3 - Mesmo formato de erro nos 3 serviços, implementado de forma independente (Priority: P3)

Como consumidor que integra com mais de um dos 3 serviços, quero que o formato do corpo de
erro `404` seja idêntico entre `order-api`, `payment-api` e `invoice-api`, para poder
escrever um único parser de erro em vez de um por serviço — mesmo sabendo que cada serviço
implementa esse tratamento de forma independente (sem biblioteca compartilhada entre eles).

**Why this priority**: valor de consistência entre serviços, mas depende que P1 e P2 já
estejam implementados em cada serviço individualmente.

**Independent Test**: comparar o corpo de erro `404` dos 3 serviços lado a lado e confirmar
que têm exatamente os mesmos nomes de campo.

**Acceptance Scenarios**:

1. **Given** uma resposta `404` de `order-api` e uma de `payment-api` (ou `invoice-api`),
   **When** os dois corpos são comparados, **Then** têm exatamente os mesmos nomes de campo,
   apesar de cada serviço ter sua própria implementação independente do tratamento de erro.

---

### Edge Cases

- Erros não relacionados a "não encontrado" (ex.: um `id` mal formado no path, que nem é um
  UUID válido) já são tratados pelo Spring por padrão como `400 Bad Request`, antes mesmo de
  chegar ao controller — fora do escopo desta feature, e não deve regredir.
- O tratamento adicionado MUST ser específico à exceção `*NotFoundException` de cada
  serviço, não um handler genérico para `RuntimeException` — um handler genérico demais
  mascararia bugs reais (erros internos passariam a aparecer como 404, escondendo o
  problema).
- O campo `message` já expõe hoje só o id que o próprio cliente informou na requisição —
  nenhuma informação nova ou sensível é exposta ao adicionar o tratamento.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada um dos 3 serviços MUST retornar HTTP `404` (não `500`) quando
  `GET /api/{recurso}/{id}` for chamado com um id inexistente.
- **FR-002**: Cada um dos 3 serviços MUST retornar HTTP `404` quando qualquer endpoint de
  ação sobre um recurso específico (`/{id}/confirm`, `/{id}/cancel` em `order-api`/
  `payment-api`; `/{id}/issue`, `/{id}/cancel` em `invoice-api`) for chamado com um id
  inexistente.
- **FR-003**: O corpo da resposta `404` MUST conter, no mínimo: `timestamp` (data/hora do
  erro), `status` (`404`), `error` (descrição curta, ex. "Not Found"), `message` (a mesma
  mensagem hoje produzida pela exceção) e `path` (o caminho da requisição que gerou o erro).
- **FR-004**: O tratamento MUST ser implementado de forma independente em cada um dos 3
  serviços (um `@RestControllerAdvice` por serviço), sem biblioteca ou módulo compartilhado
  entre eles (Princípio I da constitution).
- **FR-005**: O formato do corpo de erro (nomes e tipos dos campos) MUST ser idêntico nos 3
  serviços, apesar de cada um implementar o tratamento de forma independente.
- **FR-006**: Nenhum outro tipo de resposta (sucesso 200/201, ou outros erros já tratados
  pelo Spring como 400 de payload malformado) MUST ser afetado por esta mudança — o
  tratamento novo é específico à exceção `*NotFoundException` de cada serviço.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das chamadas a um endpoint que busca por id com um id inexistente
  retornam HTTP `404` nos 3 serviços (baseline atual: 100% retornam `500`).
- **SC-002**: 100% das respostas `404` contêm os 5 campos definidos (`timestamp`, `status`,
  `error`, `message`, `path`), verificável por qualquer cliente HTTP sem conhecer a
  implementação interna do serviço.
- **SC-003**: Um cliente HTTP consegue usar exatamente o mesmo código de parsing de erro
  para as respostas `404` dos 3 serviços, sem tratamento especial por serviço.
- **SC-004**: Nenhuma regressão nos endpoints de sucesso dos 3 serviços — toda a suíte de
  testes existente continua passando após a mudança.

## Assumptions

- O corpo de erro segue um formato inspirado no padrão de erro que o próprio Spring Boot já
  usa por convenção (`timestamp`/`status`/`error`/`message`/`path`), reconhecível por quem
  já trabalhou com APIs Spring — não é necessário adotar RFC 7807 (Problem Detail)
  formalmente nesta feature; isso pode ser revisitado depois, se o time decidir.
- `path` é o caminho da requisição (ex.: `/api/orders/<uuid>`), não a URL completa com host.
- Esta feature não introduz um handler genérico para toda `RuntimeException` — só mapeia
  especificamente a `*NotFoundException` de cada serviço.
- `invoice-api` usa `/issue` em vez de `/confirm` (nomenclatura de domínio diferente de
  `order-api`/`payment-api`), mas o mesmo princípio de tratamento de erro se aplica.
