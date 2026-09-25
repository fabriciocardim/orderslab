# Feature Specification: Testes de Integração com Testcontainers

**Feature Branch**: `011-testcontainers-integration`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Testes de integração com Testcontainers (Postgres efêmero) nos 3 microsserviços (order-api, payment-api, invoice-api). Contexto: o item 1.10 (persistência JPA real) deixou uma lacuna de teste conhecida e aceita (documentada em specs/010-jpa-persistence/spec.md FR-008 e research.md Decisão 6): os testes @SpringBootTest de integração HTTP (item 1.8, *ApplicationTests.java) hoje exigem um Postgres real alcançável em localhost:5432 (docker compose up -d postgres-api) para passar — sem isso, `mvnw test`/CI falha nesses 3 testes específicos. O objetivo deste item é fechar essa lacuna: cada serviço passa a subir um container Postgres efêmero e descartável via Testcontainers só para rodar esses testes de integração, tornando `mvnw test`/CI totalmente self-contained (não depende mais de nenhuma infra externa já rodando)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - `mvnw test`/CI roda sem depender de infra externa já provisionada (Priority: P1)

Como desenvolvedor do laboratório (ou um pipeline de CI), quero rodar `mvnw test`/`mvnw
verify` em qualquer um dos 3 serviços numa máquina limpa — sem precisar antes subir
`docker compose up -d postgres-api` nem qualquer outra infra do projeto — e ter os testes de
integração passando de verdade, para que a suíte de testes seja portável e confiável em
qualquer ambiente (minha máquina, CI, a máquina de outra pessoa), sem surpresas de "funciona
aqui mas falha lá".

**Why this priority**: é o valor central da feature — fecha exatamente a lacuna aceita no
item 1.10 (FR-008), que hoje faz `mvnw test` falhar de forma confusa (erro de conexão, não
de lógica) se ninguém tiver lembrado de subir a infra do projeto antes.

**Independent Test**: numa máquina só com o Docker Desktop rodando (nenhum `docker compose
up` do projeto executado antes), rodar `mvnw test` em cada um dos 3 serviços — a suíte
completa passa, incluindo os testes de integração HTTP (item 1.8).

**Acceptance Scenarios**:

1. **Given** uma máquina com Docker rodando mas nenhuma infra do projeto provisionada
   (`postgres-api` do `docker-compose.yml` NÃO está no ar), **When** `mvnw test` roda em
   `order-api`/`payment-api`/`invoice-api`, **Then** todos os testes passam, incluindo os de
   integração HTTP (item 1.8).
2. **Given** os mesmos 66 testes já cobertos pelos itens 1.6-1.10 (Service, Controller,
   integração HTTP), **When** exercitados contra o banco efêmero de teste, **Then** todos
   continuam passando com o mesmo comportamento observável de antes — a mudança é em como o
   banco de teste nasce, não no que é testado.
3. **Given** a configuração de produção de cada serviço (`application.properties` apontando
   para `localhost:5432`, usada quando o serviço roda de verdade), **When** inspecionada após
   esta feature, **Then** permanece inalterada — só os testes `@SpringBootTest` passam a usar
   um banco efêmero, o comportamento em execução real não muda.

---

### User Story 2 - Cada execução de teste começa de um schema limpo, criado pela migração real (Priority: P2)

Como desenvolvedor do laboratório, quero que o banco efêmero usado nos testes tenha seu
schema criado pela mesma migração Flyway usada em produção (não por um atalho de teste como
`ddl-auto`), para que os testes também validem que a migração em si está correta — e para que
cada execução comece de um estado limpo, sem dados de execuções anteriores vazando entre
rodadas de teste.

**Why this priority**: reforça o Princípio III (persistência real, inclusive em teste) e
evita o problema clássico de testes de integração "sujos" que dependem de estado deixado por
uma execução anterior — mas só faz sentido depois que a suíte já é self-contained (US1).

**Independent Test**: rodar a suíte de testes de integração duas vezes seguidas na mesma
máquina — as duas rodadas passam de forma idêntica, sem qualquer dado remanescente da
primeira rodada interferindo na segunda.

**Acceptance Scenarios**:

1. **Given** o banco de teste efêmero recém-criado (vazio), **When** o contexto Spring do
   teste de integração sobe, **Then** a migração Flyway real do serviço roda automaticamente
   e cria a tabela — o mesmo mecanismo usado em produção (item 1.10), não uma geração de
   schema exclusiva de teste.
2. **Given** uma suíte de testes já executada com sucesso, **When** ela roda novamente do
   zero, **Then** o resultado é idêntico ao da primeira execução — nenhum dado criado na
   primeira rodada persiste para a segunda, porque o banco de teste é descartado ao final de
   cada execução.

---

### Edge Cases

- **Docker não está rodando na máquina que executa os testes**: os testes `@SpringBootTest`
  MUST falhar com um erro claro de que não foi possível iniciar o container de teste — essa é
  uma dependência aceitável e documentada desta abordagem (qualquer suíte baseada em
  Testcontainers exige um runtime Docker disponível), diferente da lacuna anterior que exigia
  infra do *projeto* já provisionada e rodando.
- **Testes de Service (item 1.6) e de Controller (item 1.7)** não usam banco de dados (Service
  usa Mockito; Controller usa `@WebMvcTest` mockando o Service) — MUST continuar sem qualquer
  dependência de Docker/Testcontainers, não são afetados por esta feature.
- **Execução em paralelo dos 3 módulos** (ex.: build multi-módulo ou CI paralelo): cada
  serviço MUST subir seu próprio container de teste isolado — não há compartilhamento de
  container/estado entre os 3 serviços, preservando a independência exigida pelo Princípio I.
- **Versão da imagem Postgres usada no teste diverge da usada em produção**: MUST ser a mesma
  major version (15) usada em `infra/docker-compose.yml`, para que o teste valide o
  comportamento real da migração contra a mesma versão de banco usada fora do teste.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Os testes `@SpringBootTest` de integração HTTP (item 1.8) dos 3 serviços MUST
  rodar contra um banco de dados efêmero, criado e descartado automaticamente pela própria
  execução do teste — não contra um banco pré-existente que precise estar rodando antes.
- **FR-002**: `mvnw test`/`mvnw verify` MUST passar em qualquer um dos 3 serviços numa
  máquina onde nenhuma infra do projeto (`docker-compose.yml`) foi provisionada
  previamente — a única dependência externa aceitável é um runtime Docker disponível na
  máquina.
- **FR-003**: O schema do banco efêmero de teste MUST ser criado pela mesma migração Flyway
  usada em produção (item 1.10) — não por `ddl-auto` nem por qualquer mecanismo exclusivo de
  teste que não exista em produção.
- **FR-004**: A imagem de banco usada no teste efêmero MUST ser a mesma major version usada em
  produção (PostgreSQL 15, consistente com `infra/docker-compose.yml`).
- **FR-005**: A configuração de produção de cada serviço (`application.properties` apontando
  para `localhost:5432` e as variáveis de ambiente usadas em Compose/k8s) MUST permanecer
  inalterada — esta feature afeta somente como os testes `@SpringBootTest` obtêm seu banco,
  não como o serviço se conecta ao banco quando executado de verdade.
- **FR-006**: Todo o comportamento já validado pelos itens 1.6-1.10 (66 testes: Service,
  Controller, integração HTTP) MUST continuar passando de forma idêntica — esta feature muda
  a origem do banco de teste, não o que é testado nem o comportamento da API.
- **FR-007**: Cada execução da suíte de testes MUST partir de um banco vazio — dados criados
  por uma execução MUST NOT persistir ou interferir em execuções subsequentes.
- **FR-008**: Os testes de Service (item 1.6) e de Controller (item 1.7), que não usam banco
  de dados, MUST continuar sem qualquer dependência de Docker/Testcontainers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `mvnw test` passa nos 3 serviços numa máquina com Docker rodando e nenhuma
  outra infra do projeto previamente provisionada.
- **SC-002**: 100% dos 66 testes já cobertos pelos itens 1.6-1.10 continuam passando, sem
  nenhuma mudança de comportamento observável.
- **SC-003**: Duas execuções consecutivas da suíte de testes de integração, na mesma máquina,
  produzem o mesmo resultado — sem dados de uma rodada vazando para a próxima.
- **SC-004**: A configuração usada pelo serviço quando executado de verdade (fora de teste)
  não é alterada por esta feature — validável por diff mostrando zero mudança em
  `application.properties`/`docker-compose.yml`/manifests k8s.

## Assumptions

- Testcontainers é a ferramenta escolhida (já era a decisão sequenciada no ROADMAP desde o
  item 1.10/research.md Decisão 6) — abordagem padrão de mercado para testes de integração
  com banco real e efêmero no ecossistema Java/Spring.
- O artifact/módulo exato do Spring Boot 4.1.1 para integrar Testcontainers (nome do starter,
  pacote de `@ServiceConnection` ou equivalente) MUST ser confirmado empiricamente durante o
  planejamento técnico (`/speckit-plan`), seguindo o mesmo padrão de verificação já usado nos
  itens 1.1 e 1.10 desta sessão — não deve ser assumido a partir de conhecimento de versões
  anteriores do Spring Boot.
- Só os testes `@SpringBootTest` que hoje dependem de banco (item 1.8) são afetados — Service
  (1.6) e Controller (1.7) permanecem como estão, sem tocar em Docker.
- A máquina que roda os testes (desenvolvedor local ou CI) tem um runtime Docker disponível —
  é a mesma premissa de qualquer suíte baseada em Testcontainers, documentada como requisito
  aceitável desta feature.
- O container de teste roda a mesma imagem `postgres:15-alpine` já usada em
  `infra/docker-compose.yml`, mantendo paridade entre o que é testado e o que roda em
  produção.
