# Feature Specification: Decisão de Ownership de Schema/DB

**Feature Branch**: `009-db-ownership-decision`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Decisão de ownership de schema/DB nos 3 microsserviços (order-api, payment-api, invoice-api), pré-requisito do item 1.10 (persistência JPA real). Hoje o infra/docker-compose.yml e os manifests infra/k8s/*-deployment.yaml têm 1 único container Postgres (postgres-api) com 1 único banco (apisdb) e 1 único usuário (apis_user) compartilhado pelos 3 serviços — mesmo com os 3 apontando pro mesmo SPRING_DATASOURCE_URL. Isso fura o Princípio I (Independência dos Serviços) por baixo do capô: nada impede um serviço de acessar tabelas de outro no mesmo catálogo. A decisão é: manter 1 único container/instância Postgres compartilhado (não criar 3 containers separados, ainda é um laboratório, custo de infra não justifica isso), mas criar 3 bancos de dados lógicos separados dentro dele (order_db, payment_db, invoice_db), cada um com sua própria role/usuário dedicado (order_user, payment_user, invoice_user) e privilégios só no próprio banco — o padrão 'database per service' aplicado dentro de uma instância compartilhada, que é uma prática real de mercado para laboratórios/times pequenos. Provisionamento via script de init do Postgres (mecanismo oficial da imagem postgres: /docker-entrypoint-initdb.d/, que roda automaticamente na primeira inicialização do volume de dados) — um arquivo SQL com CREATE DATABASE/CREATE USER/GRANT para os 3, montado como volume no docker-compose.yml, e o mesmo conteúdo como ConfigMap montado no deployment do postgres-api no k8s. Os 3 serviços (docker-compose e k8s) precisam ter suas env vars de datasource (SPRING_DATASOURCE_URL/USERNAME/PASSWORD) atualizadas pra apontar pro banco/usuário dedicado de cada um, mesmo que o Spring ainda não use essas variáveis de fato (datasource continua comentado em application.properties até o item 1.10 ligar o JPA de verdade) — assim a infra já fica pronta e o item 1.10 só precisa descomentar/ativar código, sem mexer em infra de novo. Este é o item 1.9 do ROADMAP.md da Fase 1, e depende dos itens 1.1-1.8 (já concluídos)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cada serviço tem seu próprio banco e credencial, sem acesso cruzado (Priority: P1)

Como desenvolvedor do laboratório, quero que cada serviço tenha seu próprio banco de dados e
usuário dedicados dentro da instância Postgres compartilhada, para que nada (nem por engano)
permita um serviço acessar tabelas de outro — a independência que já existe no código
(Princípio I) passa a valer também na camada de persistência, antes de qualquer entidade JPA
existir.

**Why this priority**: é o valor central desta feature e pré-requisito bloqueante do item
1.10 — ligar JPA sobre um banco compartilhado seria a primeira violação real do Princípio I
desde que o laboratório começou.

**Independent Test**: subir o container `postgres-api` (isolado, sem os 3 serviços) e
confirmar, via `psql`, que existem 3 bancos (`order_db`, `payment_db`, `invoice_db`) e 3
usuários, cada um só conseguindo conectar/operar no seu próprio banco.

**Acceptance Scenarios**:

1. **Given** o container `postgres-api` recém-inicializado (volume de dados vazio), **When**
   ele termina de subir, **Then** existem 3 bancos de dados (`order_db`, `payment_db`,
   `invoice_db`) e 3 usuários (`order_user`, `payment_user`, `invoice_user`).
2. **Given** o usuário `order_user`, **When** uma conexão é tentada contra `payment_db` (ou
   `invoice_db`), **Then** a conexão/operação é rejeitada por falta de privilégio.
3. **Given** o usuário `order_user`, **When** uma conexão é tentada contra `order_db`,
   **Then** a conexão funciona e o usuário tem privilégios completos nesse banco.
4. **Given** os manifests de `docker-compose.yml` e `infra/k8s/*-deployment.yaml` dos 3
   serviços, **When** inspecionados, **Then** cada serviço tem `SPRING_DATASOURCE_URL`/
   `USERNAME`/`PASSWORD` apontando para o seu próprio banco/usuário dedicado — nenhum dos 3
   aponta mais para `apisdb`/`apis_user`.

---

### Edge Cases

- O script de inicialização só roda automaticamente na **primeira** inicialização do volume
  de dados do Postgres (mecanismo padrão da imagem oficial) — se um volume de dados antigo
  (com o `apisdb` compartilhado) já existir localmente, ele precisa ser removido para o novo
  provisionamento rodar; isso é esperado e MUST ser documentado, não um bug desta feature.
- Os 3 serviços ainda **não usam** essas credenciais de fato nesta feature — o datasource
  continua comentado em `application.properties` até o item 1.10; esta feature só garante
  que a infra já está correta quando esse interruptor for ligado.
- Senhas em texto plano nos manifests (compose e k8s) — já é uma limitação conhecida e
  documentada do laboratório (ver épico E4.2 do ROADMAP, "Secrets externalizados"); esta
  feature não piora nem resolve isso, só replica o mesmo padrão já usado hoje (texto plano)
  para as novas credenciais por serviço.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A instância Postgres MUST continuar sendo um único container/pod compartilhado
  (não criar instâncias separadas por serviço) — decisão de custo/complexidade de infra para
  um laboratório.
- **FR-002**: Dentro dessa instância, MUST existir um banco de dados lógico dedicado por
  serviço (`order_db`, `payment_db`, `invoice_db`), provisionado automaticamente na primeira
  inicialização do container via mecanismo de init script padrão da imagem `postgres`.
- **FR-003**: Cada banco MUST ter um usuário/role dedicado (`order_user`, `payment_user`,
  `invoice_user`) com privilégios completos apenas no seu próprio banco — nenhum privilégio
  nos bancos dos outros 2 serviços.
- **FR-004**: O provisionamento MUST ser definido como código versionado (script SQL) — não
  um passo manual — e MUST funcionar de forma idêntica em `docker-compose` (volume montado)
  e Kubernetes (`ConfigMap` montado no deployment do `postgres-api`).
- **FR-005**: As 3 entradas de serviço em `docker-compose.yml` e os 3 manifests de
  deployment em `infra/k8s/` MUST ter suas env vars de datasource atualizadas para apontar
  ao banco/usuário dedicado do respectivo serviço.
- **FR-006**: Nenhuma mudança de código Java (`application.properties`, `pom.xml`, entidades)
  MUST ocorrer nesta feature — datasource continua desabilitado até o item 1.10 (Princípio
  III da constitution).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A instância Postgres provisiona 3 bancos + 3 usuários automaticamente na
  primeira inicialização, sem nenhum passo manual.
- **SC-002**: 100% das tentativas de um usuário de serviço acessar o banco de outro serviço
  são rejeitadas.
- **SC-003**: `docker-compose.yml` e os 3 manifests `infra/k8s/*-deployment.yaml` não contêm
  mais nenhuma referência a `apisdb`/`apis_user` — cada serviço aponta só para o seu próprio
  banco/usuário.
- **SC-004**: Os 3 serviços continuam buildando e passando em todos os testes existentes
  (itens 1.1-1.8) sem nenhuma mudança de comportamento — esta feature é só infraestrutura,
  ainda não consumida pelo código Java.

## Assumptions

- "Database per service" dentro de uma instância Postgres compartilhada é uma escolha
  deliberada de custo/complexidade para o estágio de laboratório — não é o mesmo nível de
  isolamento de 3 instâncias totalmente separadas, mas evita triplicar a infraestrutura a
  gerenciar (3 Deployments/StatefulSets, 3 volumes, 3 conjuntos de credenciais de
  administração) sem abrir mão do isolamento lógico que o Princípio I exige.
- O provisionamento reaproveita o mecanismo `/docker-entrypoint-initdb.d/` já documentado
  pela imagem oficial `postgres`, sem introduzir uma ferramenta de migração nova (Flyway/
  Liquibase) — essa decisão fica para o item 1.10, quando o schema de cada serviço (tabelas)
  for de fato criado.
- Nomes escolhidos (`order_db`/`payment_db`/`invoice_db`, `order_user`/`payment_user`/
  `invoice_user`) seguem a convenção em inglês já usada no restante do projeto (nomes de
  serviço, pastas, classes).
