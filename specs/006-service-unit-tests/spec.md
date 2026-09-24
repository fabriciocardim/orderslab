# Feature Specification: Testes Unitários de Service

**Feature Branch**: `006-service-unit-tests`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Testes unitários de Service nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje só existe o placeholder padrão *ApplicationTests.java com um contextLoads() vazio em cada serviço — nenhum teste real de OrderService/PaymentService/InvoiceService existe. O objetivo é cobrir, via JUnit 5 (e Mockito se necessário), sem subir contexto Spring: criação do recurso, busca por id existente, 404 (exceção NotFound) em id inexistente, transição válida a partir do estado inicial (item 1.5), e transição bloqueada (409/InvalidStatusTransitionException, item 1.5) para cada ação (confirm/cancel em order-api e payment-api; issue/cancel em invoice-api). Esclarecimento importante: cobertura de Bean Validation (item 1.4) fica fora do escopo de testes unitários de Service, porque @Valid roda na camada MVC antes do Service ser chamado — isso é responsabilidade do item 1.7 (testes de Controller). Também não há nada para mockar com Mockito hoje, já que os 3 Services não têm nenhuma dependência injetada (o armazenamento é um Map criado internamente) — os testes serão JUnit puro, instanciando o Service diretamente; Mockito só passa a fazer sentido quando a persistência real (JPA, item 1.10) introduzir um Repository injetado. Este é o item 1.6 do ROADMAP.md da Fase 1, e depende dos itens 1.1-1.5 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Regressão de comportamento do Service é detectada automaticamente (Priority: P1)

Como desenvolvedor do laboratório, quero que uma suíte de testes automatizados cubra o
comportamento central de cada `Service` (criação, busca, 404, transições válidas e
bloqueadas), para que uma mudança futura que quebre esse comportamento seja pega
imediatamente, sem depender de testar manualmente com `curl`.

**Why this priority**: até aqui, cada feature da Fase 1 foi validada manualmente (subir o
serviço, `curl`, conferir a resposta) — funciona, mas não protege contra regressão em
mudanças futuras (Fase 2 em diante). Automatizar isso é o valor central desta feature.

**Independent Test**: rodar `./mvnw test` (ou `./mvnw clean verify`) em cada um dos 3
serviços e observar os novos testes de Service passando.

**Acceptance Scenarios**:

1. **Given** o `OrderService`, **When** um pedido é criado, **Then** ele é retornado com
   status `PENDING` e os dados corretos, e passa a aparecer em `findAll()`/`findById()`.
2. **Given** um pedido existente, **When** `findById()` é chamado com o id correto, **Then**
   o pedido correto é retornado.
3. **Given** um id inexistente, **When** `findById()`/`confirm()`/`cancel()` é chamado,
   **Then** `OrderNotFoundException` é lançada.
4. **Given** um pedido `PENDING`, **When** `confirm()` (ou `cancel()`) é chamado, **Then** a
   transição ocorre e o novo status é retornado.
5. **Given** um pedido que já saiu do estado `PENDING` (ex.: já `CONFIRMED`), **When**
   `confirm()` ou `cancel()` é chamado de novo, **Then**
   `InvalidStatusTransitionException` é lançada.
6. **Given** os cenários 1-5, **When** aplicados a `PaymentService` (estado inicial
   `RESERVED`) e a `InvoiceService` (estado inicial `PENDING`, ação `issue` no lugar de uma
   das duas), **Then** o mesmo comportamento se confirma nos outros 2 serviços.

---

### Edge Cases

- Bean Validation (`@NotBlank`/`@Positive`/`@Pattern`, item 1.4) roda na camada MVC via
  `@Valid`, antes do `Service` ser chamado — um teste unitário de `Service` não consegue (e
  não deve tentar) exercitar essa validação; isso é coberto pelo item 1.7 (testes de
  Controller).
- Os 3 `Service` não têm nenhuma dependência injetada hoje (armazenamento é um `Map` interno
  criado no próprio campo) — não há nada para mockar com Mockito nesta feature; os testes
  instanciam o `Service` diretamente.
- `findAll()` com nenhum recurso criado ainda (lista vazia) é um caso trivial, mas MUST ser
  coberto para garantir que o serviço não lança exceção nesse caso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada um dos 3 `Service` MUST ter uma suíte de testes cobrindo: criação,
  `findById` com sucesso, `findById`/ação com id inexistente (exceção `*NotFoundException`),
  transição válida a partir do estado inicial, e transição bloqueada a partir de um estado
  não-inicial (`InvalidStatusTransitionException`).
- **FR-002**: Os testes MUST rodar sem subir o contexto Spring (nem `@SpringBootTest` nem
  `@WebMvcTest`) — instanciação direta do `Service` via JUnit 5.
- **FR-003**: Os testes de cada serviço MUST ser implementados de forma independente
  (Princípio I da constitution) — sem classe base de teste compartilhada entre os 3.
- **FR-004**: Os testes MUST passar com o código atual dos 3 serviços (nenhuma mudança de
  comportamento de produção é esperada nesta feature — só cobertura de teste).
- **FR-005**: `findAll()` sem nenhum recurso criado MUST retornar uma lista vazia, sem
  lançar exceção — coberto por teste.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Os 3 serviços passam a ter, cada um, uma classe de teste de `Service` cobrindo
  no mínimo os 5 cenários do FR-001 (baseline atual: 0 testes reais de `Service` em qualquer
  um dos 3).
- **SC-002**: `./mvnw test` roda com sucesso (todos os testes passando) nos 3 serviços após
  esta feature, sem nenhuma mudança no código de produção.
- **SC-003**: Uma futura quebra de comportamento em qualquer um dos 5 cenários cobertos (ex.:
  alguém remove a guarda de transição do item 1.5 sem querer) faz pelo menos 1 teste falhar.

## Assumptions

- JUnit 5 (Jupiter) e AssertJ já estão disponíveis nos 3 serviços via as dependências de
  teste existentes (`spring-boot-starter-*-test`, itens 1.1-1.3) — nenhuma dependência nova
  é necessária.
- Mockito também já está disponível transitivamente, mas não é usado nesta feature (nada a
  mockar hoje); fica disponível para quando a persistência real (item 1.10) introduzir um
  `Repository` injetado no `Service`.
- Cobertura de Bean Validation (item 1.4) e de contrato HTTP completo (status code, corpo de
  erro) fica para o item 1.7 (testes de Controller via `@WebMvcTest`) — fora do escopo desta
  feature.
