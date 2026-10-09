# Feature Specification: Persistência JPA Simétrica

**Feature Branch**: `010-jpa-persistence`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Persistência JPA simétrica nos 3 microsserviços (order-api, payment-api, invoice-api). Hoje o armazenamento é 100% em memória (ConcurrentHashMap dentro de cada Service) — nada sobrevive a um restart. O objetivo: adicionar spring-data-jpa + driver postgresql (já ativo comentado em order-api, ausente em payment-api/invoice-api) + Flyway (flyway-core + flyway-database-postgresql, versão 12.4.0 já gerenciada pelo BOM do Spring Boot 4.1.1, confirmado via dependency:get) aos 3 pom.xml. Anotar as classes Model existentes (Order/Payment/Invoice) diretamente como @Entity (não criar uma hierarquia paralela) — field access, sem @GeneratedValue já que o id já é atribuído via UUID.randomUUID() no construtor; status mapeado com @Enumerated(EnumType.STRING) (nunca ordinal, já que os itens 1.5 já removeu valores desses enums uma vez, ordinal seria frágil). Criar Repository (OrderRepository/PaymentRepository/InvoiceRepository extends JpaRepository<Entidade, UUID>) substituindo o Map em cada Service — o Service passa a receber o Repository via injeção de construtor. Criar migrations Flyway (V1__create_<tabela>_table.sql) por serviço, com nomes de tabela/coluna no padrão snake_case (orders/payments/invoices). Ativar spring.datasource.*/spring.jpa.* nos 3 application.properties, apontando para localhost:5432 com o banco/usuário dedicado de cada serviço (já provisionados no item 1.9 — order_db/order_user etc.); spring.jpa.hibernate.ddl-auto=validate (Flyway é dono do schema, Hibernate só valida que a entidade bate com a tabela, nunca gera DDL sozinho). Os testes de Service existentes (item 1.6, OrderServiceTest etc.) instanciavam o Service direto sem dependências — agora precisam ser atualizados para usar Mockito (@Mock do Repository, @InjectMocks no Service) já que o Service passa a ter uma dependência real injetada — isso é exatamente o gatilho que o item 1.6 já tinha previsto para quando a persistência real chegasse. Importante: os testes @SpringBootTest de integração (item 1.8) e os que usam o contexto completo passam a precisar de um Postgres real alcançável em localhost:5432 (docker compose up -d postgres-api, já provisionado com os 3 bancos pelo item 1.9) para passar — isso é uma lacuna conhecida e aceita, resolvida propriamente pelo item 1.11 (Testcontainers), que já está sequenciado logo em seguida no ROADMAP; enquanto isso, rodar `mvnw test`/CI sem um Postgres real vai falhar nesses testes específicos, e isso deve ficar registrado claramente. Este é o item 1.10 do ROADMAP.md da Fase 1, e depende dos itens 1.1-1.9 (já concluídos, incluindo a decisão de banco/usuário dedicado por serviço do item 1.9)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dados sobrevivem a um restart do serviço (Priority: P1)

Como desenvolvedor do laboratório, quero que um pedido/pagamento/nota criado continue
existindo depois que o serviço reinicia, para que o sistema se comporte como uma API real,
não como uma simulação que perde tudo a cada deploy.

**Why this priority**: é o valor central da feature — fecha a lacuna mais visível do
laboratório até aqui (Princípio III da constitution já exige isso desde a v3.0.0, mas a
implementação estava pendente até este item).

**Independent Test**: criar um recurso via API, reiniciar o container do serviço, buscar o
mesmo recurso de novo — ele continua lá, com os mesmos dados.

**Acceptance Scenarios**:

1. **Given** um pedido criado via `POST /api/orders`, **When** o container `order-api` é
   reiniciado, **Then** `GET /api/orders/{id}` continua retornando o mesmo pedido, com os
   mesmos dados.
2. **Given** os mesmos cenários de sucesso já cobertos pelos itens 1.3-1.8 (criação, busca,
   404, validação, transições válidas/bloqueadas, contrato HTTP), **When** exercitados contra
   a versão com persistência real, **Then** o comportamento observável pela API MUST
   permanecer idêntico ao que já era garantido em memória — a mudança é de onde o dado mora,
   não do que a API promete.
3. **Given** os 3 serviços, **When** inspecionados, **Then** cada um usa exatamente o mesmo
   padrão de persistência (Entity + Repository + migration Flyway) — nenhuma solução
   diferente por serviço, apesar de cada um ter seu próprio banco dedicado (item 1.9).

---

### User Story 2 - Schema do banco é versionado, não inventado em runtime (Priority: P2)

Como desenvolvedor do laboratório, quero que a estrutura das tabelas (`orders`/`payments`/
`invoices`) seja definida por um arquivo de migração versionado no repositório, para que
qualquer pessoa consiga recriar o schema do zero de forma determinística, e para que o
Hibernate nunca gere DDL "por conta própria" em produção.

**Why this priority**: é uma prática de mercado consolidada (migração como código,
versionada), mas o valor só se realiza depois que a persistência em si já existe (US1).

**Independent Test**: apagar o banco de um serviço, reiniciar o serviço, e confirmar que a
tabela é recriada automaticamente a partir do arquivo de migração — sem qualquer passo
manual.

**Acceptance Scenarios**:

1. **Given** um banco de dados vazio (ex.: após recriar o container `postgres-api`), **When**
   o serviço correspondente sobe, **Then** a migração Flyway roda automaticamente e cria a
   tabela do serviço.
2. **Given** a tabela criada pela migração, **When** o Hibernate valida o mapeamento da
   entidade contra ela na inicialização, **Then** a validação passa sem erro — confirmando
   que a entidade e a migração estão de acordo.

---

### Edge Cases

- **Testes que dependem de contexto Spring completo** (item 1.8, e qualquer teste futuro que
  suba `@SpringBootTest` com o datasource ativo) passam a exigir um Postgres real alcançável
  em `localhost:5432` — sem ele, esses testes falham na inicialização do contexto (erro de
  conexão), não por um problema de lógica. Isso é uma lacuna conhecida e aceita nesta
  feature, resolvida pelo item 1.11 (Testcontainers), sequenciado logo em seguida.
- **Testes de Service** (item 1.6) que hoje instanciam o `Service` sem argumentos deixam de
  compilar assim que o `Service` passa a exigir um `Repository` no construtor — MUST ser
  atualizados como parte desta feature (não é opcional, é um requisito de compilação).
- Valores de enum removidos no item 1.5 (`OrderStatus.COMPLETED`,
  `PaymentStatus.PENDING`/`FAILED`, `InvoiceStatus.FAILED`) não têm linha correspondente no
  banco — isso é esperado, já que esses valores nunca são atribuídos por nenhum código.
- O `ddl-auto=validate` significa que, se a migração e a entidade divergirem (ex.: alguém
  adiciona um campo na entidade sem criar uma migração nova), o serviço MUST falhar ao
  iniciar com um erro claro — em vez de o Hibernate "consertar" silenciosamente o schema.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Os 3 serviços MUST persistir seus dados em PostgreSQL real (no banco dedicado
  provisionado pelo item 1.9), substituindo o armazenamento em memória atual.
- **FR-002**: Cada serviço MUST usar exatamente o mesmo padrão de implementação (Entity +
  Repository Spring Data JPA + migração Flyway) — implementado de forma independente por
  serviço (Princípio I da constitution), sem código compartilhado entre eles.
- **FR-003**: O status de cada entidade (`OrderStatus`/`PaymentStatus`/`InvoiceStatus`) MUST
  ser armazenado como texto legível (nome do enum), nunca como número — para não quebrar em
  silêncio se a ordem dos valores do enum mudar no futuro (já aconteceu no item 1.5).
- **FR-004**: A estrutura de cada tabela MUST ser definida por um arquivo de migração
  versionado no repositório, aplicado automaticamente na inicialização do serviço — não por
  geração automática de schema em runtime.
- **FR-005**: Na inicialização, cada serviço MUST validar que o mapeamento das entidades
  corresponde ao schema real do banco, falhando de forma clara se houver divergência.
- **FR-006**: Todo o comportamento de API já garantido pelos itens 1.3-1.8 (status codes,
  formato de erro, guardas de transição, validação de request) MUST continuar valendo
  exatamente como antes — esta feature muda onde o dado é guardado, não o contrato da API.
- **FR-007**: Os testes de Service existentes (item 1.6) MUST ser adaptados para continuar
  passando com a nova dependência de `Repository` no `Service` (via mock), sem perder
  cobertura dos cenários já testados.
- **FR-008**: A feature MUST deixar registrado, de forma clara, que testes dependentes de
  contexto Spring completo (item 1.8) passam a exigir um Postgres real disponível para
  passar — não é um requisito desta feature resolver isso definitivamente (isso é escopo do
  item 1.11).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um recurso criado via API continua existindo (mesmos dados) depois de um
  restart do container do serviço, nos 3 serviços.
- **SC-002**: 100% dos cenários de sucesso e erro já cobertos pelos itens 1.3-1.8 continuam
  passando com o comportamento idêntico ao anterior, quando exercitados contra um Postgres
  real.
- **SC-003**: Um banco vazio, ao subir o serviço correspondente, tem sua tabela criada
  automaticamente pela migração — sem nenhum passo manual (`CREATE TABLE`, etc.).
- **SC-004**: Os testes de Service (item 1.6) continuam cobrindo os mesmos 7+ cenários por
  serviço já documentados, agora mockando o `Repository` em vez de instanciar um
  armazenamento fake.

## Assumptions

- Flyway (não Liquibase, não `ddl-auto`) é a ferramenta de migração escolhida — SQL puro,
  amplamente adotado no ecossistema Spring, e já totalmente gerenciado pelo BOM do Spring
  Boot 4.1.1 (versão 12.4.0, confirmada antes desta spec).
- `spring.jpa.hibernate.ddl-auto=validate` é o modo escolhido (não `update`/`create`) —
  consistente com "Flyway é dono do schema" e evita que o Hibernate altere tabelas em
  produção silenciosamente.
- As classes `Order`/`Payment`/`Invoice` existentes viram as próprias entidades JPA (via
  anotação direta) — não se cria uma segunda hierarquia de classes só para persistência,
  mantendo o código simples (Princípio II da constitution).
- `application.properties` aponta para `localhost:5432` (porta já exposta pelo
  `docker-compose.yml` do item 1.9) — o `docker-compose.yml`/manifests k8s continuam
  sobrescrevendo isso via variável de ambiente (`SPRING_DATASOURCE_URL=...postgres-api...`)
  quando o serviço roda dentro de um container, sem precisar de configuração adicional nesta
  feature.
- A lacuna de testes de integração exigindo um Postgres real (em vez de hermético) é aceita
  como intencional e temporária — resolvida pelo item 1.11 (Testcontainers), já sequenciado
  como o próximo item do ROADMAP.
