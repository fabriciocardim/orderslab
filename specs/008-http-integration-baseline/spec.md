# Feature Specification: Teste de Integração HTTP Baseline

**Feature Branch**: `008-http-integration-baseline`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Teste de integração HTTP baseline (@SpringBootTest) nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje cada serviço só tem o placeholder padrão *ApplicationTests.java com um contextLoads() vazio, que só confirma que o contexto Spring sobe, sem exercitar nenhum fluxo real. O objetivo é substituir esse placeholder por um teste de integração de verdade: subir o contexto completo (@SpringBootTest com webEnvironment=RANDOM_PORT, servidor real, sem mocks) e exercitar o fluxo criar→confirmar (ou issue)/cancelar→buscar via HTTP real, usando o RestTestClient (novo em Spring Framework 7/Spring Boot 4.1.1 — confirmei via inspeção de bytecode que é a ferramenta atual para isso, API fluente igual ao WebTestClient: .get()/.post().uri(...).exchange().expectStatus()...expectBody().jsonPath(...); habilitado via @AutoConfigureRestTestClient, injetável direto sem precisar de @LocalServerPort porque ele já detecta a porta aleatória do servidor local). Diferente dos itens 1.6 (Service isolado) e 1.7 (Controller com Service mockado), aqui nada é mockado — é o fluxo real ponta a ponta, ainda em memória (não depende de persistência real, que só chega no item 1.10). Este é o item 1.8 do ROADMAP.md da Fase 1, e depende dos itens 1.1-1.7 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fluxo real ponta a ponta é validado sem nenhum mock (Priority: P1)

Como desenvolvedor do laboratório, quero um teste que suba cada serviço de verdade (contexto
Spring completo, servidor real numa porta aleatória, nada mockado) e exercite o fluxo
completo — criar, aplicar a transição válida, buscar de novo — para ter a garantia de que
tudo funciona junto, não só em partes isoladas (Service sozinho no item 1.6, Controller com
Service mockado no item 1.7).

**Why this priority**: é o valor central desta feature — fecha a lacuna entre "as partes
funcionam isoladamente" (itens 1.6/1.7) e "o serviço inteiro funciona de ponta a ponta",
substituindo o placeholder vazio que existe desde o início do laboratório.

**Independent Test**: rodar `./mvnw test` em cada um dos 3 serviços e observar o teste de
integração passando.

**Acceptance Scenarios**:

1. **Given** o serviço rodando com contexto completo, **When** um recurso é criado via HTTP
   real, **Then** a resposta confirma `201` e o recurso criado está no estado inicial
   correto.
2. **Given** o recurso criado no cenário 1, **When** a transição válida é aplicada via HTTP
   real (`confirm`/`issue`), **Then** a resposta confirma `200` e o novo status.
3. **Given** o recurso já transicionado, **When** ele é buscado de novo via `GET /{id}`,
   **Then** a resposta reflete o status atualizado — prova de que a mudança persistiu no
   armazenamento em memória entre requisições HTTP diferentes (não só dentro de uma única
   chamada de método, como nos testes de Service do item 1.6).
4. **Given** o mesmo fluxo, **When** a ação de cancelamento é testada num segundo recurso
   criado à parte, **Then** o comportamento de sucesso (`200`) também é confirmado para essa
   segunda ação de cada serviço.

---

### Edge Cases

- Diferente do item 1.7, aqui não existe `@MockitoBean` — qualquer falha de integração real
  entre `Controller`, `Service` e o `GlobalExceptionHandler` (ex.: um bean não encontrado, um
  `@Valid` mal configurado) aparece como falha deste teste, não dos anteriores.
- O teste roda contra o armazenamento em memória atual — não valida persistência entre
  reinicializações do processo (isso é escopo de integração real com banco, item 1.11).
- Este item substitui (não adiciona a) o `contextLoads()` vazio — o arquivo
  `*ApplicationTests.java` de cada serviço passa a conter o teste de fluxo real.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada um dos 3 serviços MUST ter um teste `@SpringBootTest` com servidor real
  (porta aleatória) que substitui o `contextLoads()` vazio atual.
- **FR-002**: O teste MUST exercitar, via chamadas HTTP reais (não mockadas): criar o
  recurso, aplicar uma transição válida a partir do estado inicial, e buscar o recurso de
  novo para confirmar que o novo estado persistiu no armazenamento em memória entre
  requisições.
- **FR-003**: O teste MUST cobrir as 2 ações de transição de cada serviço (`confirm`+
  `cancel` em order-api/payment-api; `issue`+`cancel` em invoice-api), cada uma em pelo
  menos um cenário de sucesso ponta a ponta.
- **FR-004**: O teste MUST ser implementado de forma independente em cada um dos 3 serviços
  (Princípio I da constitution) — sem classe base compartilhada.
- **FR-005**: O teste MUST passar com o código atual dos 3 serviços (nenhuma mudança de
  comportamento de produção é esperada nesta feature — só cobertura de teste).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Os 3 serviços passam a ter um teste de integração real cobrindo o fluxo
  completo criar→transicionar→buscar via HTTP (baseline atual: 0 — só o `contextLoads()`
  vazio existe).
- **SC-002**: `./mvnw test` roda com sucesso (todos os testes — itens 1.6, 1.7 e este) nos 3
  serviços após esta feature, sem nenhuma mudança no código de produção.
- **SC-003**: Uma futura quebra na integração real entre as camadas (ex.: um bean mal
  configurado que só falha em runtime, não nos testes com mock dos itens 1.6/1.7) faz este
  teste falhar.

## Assumptions

- `RestTestClient` (`org.springframework.test.web.servlet.client.RestTestClient`, Spring
  Framework 7) é a ferramenta usada, habilitada via `@AutoConfigureRestTestClient`
  (`org.springframework.boot.resttestclient.autoconfigure`) — confirmado via inspeção de
  bytecode antes desta spec como o substituto atual do `TestRestTemplate` clássico para este
  cenário (`@SpringBootTest` com servidor real).
- O teste não depende de persistência real — continua rodando contra o armazenamento em
  memória atual dos 3 serviços; isso é intencional e consistente com o Princípio III da
  constitution só passar a exigir persistência real quando a feature em questão tocar esse
  aspecto (aqui não toca).
- Testes de integração com banco real via Testcontainers ficam para o item 1.11, depois que
  a persistência real (item 1.10) existir.
