# Feature Specification: Testes de Controller (@WebMvcTest)

**Feature Branch**: `007-controller-tests`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Testes de Controller (@WebMvcTest) nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje o contrato HTTP completo (status codes 200/201/400/404/409 e o shape do corpo de erro dos itens 1.3/1.4/1.5) nunca foi validado por um teste automatizado — só manualmente via curl durante cada feature anterior. O objetivo é, para cada um dos 3 controllers, um teste @WebMvcTest mockando o Service correspondente (@MockitoBean — confirmei que @MockBean foi totalmente removida no Spring Boot 4.1.1/Spring Framework 7, só existe @MockitoBean em org.springframework.test.context.bean.override.mockito.MockitoBean; @WebMvcTest também mudou de pacote para org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest) e usando MockMvc para exercitar: criação com corpo válido (201), criação com corpo inválido (400, valida o campo validationErrors do ErrorResponse do item 1.4), busca por id existente (200) e inexistente (404, valida o shape do ErrorResponse do item 1.3), listagem (200), transição válida (200) e transição bloqueada (409, valida o shape do item 1.5) para cada uma das 2 ações de cada serviço. Diferente do item 1.6 (testes de Service, sem contexto Spring), aqui o Service é mockado e o que se testa é a fiação real da camada MVC: roteamento, serialização/deserialização JSON, Bean Validation real (@Valid), e o GlobalExceptionHandler real (@RestControllerAdvice) traduzindo cada exceção pro status HTTP correto. Este é o item 1.7 do ROADMAP.md da Fase 1, e depende dos itens 1.1-1.6 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contrato HTTP de cada API é validado automaticamente (Priority: P1)

Como desenvolvedor do laboratório, quero que o contrato HTTP completo de cada API (status
codes e shape do corpo de erro) seja validado por testes automatizados, para que uma mudança
futura que quebre esse contrato (ex.: alguém remove sem querer o `@Valid` de um controller,
ou desconfigura o `GlobalExceptionHandler`) seja pega imediatamente — sem depender de testar
manualmente com `curl` de novo, como foi feito nos itens 1.3-1.5.

**Why this priority**: é o valor central desta feature — cobre exatamente a camada (roteamento
HTTP, serialização JSON, validação real, tradução de exceção pra status code) que os testes
de Service (item 1.6) explicitamente não conseguem alcançar.

**Independent Test**: rodar `./mvnw test` em cada um dos 3 serviços e observar os novos
testes de Controller passando.

**Acceptance Scenarios**:

1. **Given** um corpo de requisição válido, **When** `POST` de criação é chamado em
   qualquer uma das 3 APIs, **Then** a resposta é `201` com o corpo esperado.
2. **Given** um corpo de requisição inválido (ex.: campo obrigatório ausente), **When** o
   mesmo `POST` é chamado, **Then** a resposta é `400` com `validationErrors` listando o
   problema — sem que o `Service` seja sequer invocado.
3. **Given** um id existente, **When** `GET /{id}` é chamado, **Then** a resposta é `200`
   com o recurso correto.
4. **Given** um id inexistente, **When** `GET /{id}` (ou qualquer ação) é chamado, **Then**
   a resposta é `404` com o corpo de erro no formato já estabelecido (item 1.3).
5. **Given** nenhum recurso ou vários recursos, **When** `GET` (listagem) é chamado,
   **Then** a resposta é `200` com a lista correspondente.
6. **Given** um recurso no seu estado inicial acionável, **When** uma ação de transição
   válida é chamada (`confirm`/`cancel` em order-api/payment-api; `issue`/`cancel` em
   invoice-api), **Then** a resposta é `200`.
7. **Given** um recurso fora do seu estado inicial, **When** a mesma ação é chamada de novo,
   **Then** a resposta é `409` com o corpo de erro no formato já estabelecido (item 1.5).

---

### Edge Cases

- O `Service` de cada controller é substituído por um mock nesta feature — o comportamento
  real do `Service` (criação, transições, 404/409 internos) já está coberto pelo item 1.6;
  aqui o mock só precisa devolver ou lançar o que cada cenário exige, sem reimplementar a
  lógica de negócio.
- `@WebMvcTest` carrega automaticamente o `@RestControllerAdvice` do próprio serviço (é
  descoberto pelo scan da camada web, não precisa ser declarado manualmente no teste) —
  garantindo que o `GlobalExceptionHandler` real (não um substituto) seja exercitado.
- Bean Validation roda de verdade nesta camada (diferente do item 1.6): um corpo inválido
  MUST nunca chegar a invocar o `Service` mockado — se isso acontecer, é sinal de que
  `@Valid` não está mais no controller.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada um dos 3 `Controller` MUST ter uma suíte de testes `@WebMvcTest`
  cobrindo: criação válida (`201`), criação inválida (`400` com `validationErrors`), busca
  existente (`200`), busca inexistente (`404`), listagem (`200`), e para cada uma das 2
  ações de transição do serviço: sucesso (`200`) e conflito (`409`).
- **FR-002**: Os testes MUST mockar o `Service` correspondente (não usar a implementação
  real) — o foco é a camada MVC, cuja cobertura de lógica de negócio já pertence ao item
  1.6.
- **FR-003**: Os testes de cada serviço MUST ser implementados de forma independente
  (Princípio I da constitution) — sem classe base de teste compartilhada entre os 3.
- **FR-004**: Os corpos de erro `404`/`409` verificados MUST corresponder exatamente ao
  formato já estabelecido nos itens 1.3/1.5 (`timestamp`, `status`, `error`, `message`,
  `path`); o corpo `400` MUST adicionalmente conter `validationErrors` não vazio (item 1.4).
- **FR-005**: Os testes MUST passar com o código atual dos 3 serviços (nenhuma mudança de
  comportamento de produção é esperada nesta feature — só cobertura de teste).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Os 3 serviços passam a ter, cada um, uma classe de teste de `Controller`
  cobrindo no mínimo os 7 cenários do FR-001 (baseline atual: 0 testes automatizados de
  contrato HTTP em qualquer um dos 3).
- **SC-002**: `./mvnw test` roda com sucesso (todos os testes, incluindo os do item 1.6)
  nos 3 serviços após esta feature, sem nenhuma mudança no código de produção.
- **SC-003**: Uma futura quebra no contrato HTTP (ex.: remover `@Valid` de um controller, ou
  um handler do `GlobalExceptionHandler` parar de mapear pro status certo) faz pelo menos 1
  teste desta feature falhar.

## Assumptions

- `@WebMvcTest` e `@MockitoBean` já estão disponíveis via as dependências de teste
  existentes (`spring-boot-starter-webmvc-test`, item 1.1); nenhuma dependência nova é
  necessária. Pacotes exatos confirmados antes desta spec:
  `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` e
  `org.springframework.test.context.bean.override.mockito.MockitoBean` (Spring Boot
  4.1.1/Spring Framework 7 — `@MockBean` foi totalmente removida nesta versão).
- Cobertura de lógica de negócio (transições, armazenamento) permanece no item 1.6 — esta
  feature não duplica esses casos, só verifica que a camada HTTP os expõe corretamente.
- Testes de integração ponta a ponta com banco de dados real (Testcontainers) ficam para o
  item 1.11, depois que a persistência real (item 1.10) existir.
