# Research: Persistência JPA Simétrica

## Decisão 1: Flyway (não Liquibase, não `ddl-auto`)

**Decision**: Flyway, com `spring-boot-starter-flyway` (não `flyway-core` cru) +
`flyway-database-postgresql` (sem `<version>` explícita — gerenciada pelo
`spring-boot-dependencies:4.1.1`).

**Rationale**: confirmado via `./mvnw dependency:get`/`dependency:tree` que os artifacts
resolvem; e via inspeção do `spring-boot-dependencies-4.1.1.pom` que a versão gerenciada é
**12.4.0** — nenhuma versão precisa ser fixada manualmente no `pom.xml` de cada serviço.
Flyway é SQL puro (migrations `.sql` simples), o padrão mais comum no ecossistema Spring, e
desde a v10 exige o módulo `flyway-database-postgresql` separado (mudança da própria
ferramenta, não do Spring Boot).

**Correção empírica durante a implementação (T005)**: adicionar só `flyway-core` +
`flyway-database-postgresql` (como este documento originalmente previa) resolve no classpath
mas **não é auto-configurado** — Spring Boot 4.1.1 moveu `FlywayAutoConfiguration` para um
módulo dedicado `spring-boot-flyway`, só trazido transitivamente pelo starter
`spring-boot-starter-flyway` (mesmo padrão de granularização já visto em `-webmvc`/`-kafka`
no item 1.1). Sem o starter, a app sobe com o datasource configurado mas sem nunca rodar a
migração — Hibernate falha com `SchemaManagementException: missing table`, silenciosamente
sem qualquer log de Flyway. Confirmado via `mvnw test` real: com `flyway-core` cru, a
`flyway_schema_history` nunca era criada; trocando para `spring-boot-starter-flyway` (que traz
`spring-boot-flyway` + `spring-boot-starter-jdbc` transitivamente), a migração passou a rodar
e todos os 22 testes de `order-api` passaram.

**Alternatives considered**:
- Liquibase: mais recursos (XML/YAML/JSON além de SQL), mas mais complexo do que o
  laboratório precisa agora.
- `spring.jpa.hibernate.ddl-auto=update`/`create`: rejeitado — deixaria o Hibernate "dono"
  do schema, o oposto do que o Princípio II (funcionalidade técnica real) e a prática de
  mercado recomendam; schema deve ser código versionado, não efeito colateral do Hibernate.

## Decisão 2: Entidades JPA = classes Model existentes

**Decision**: anotar `Order`/`Payment`/`Invoice` diretamente com `@Entity`/`@Table`/`@Id`/
`@Enumerated(EnumType.STRING)`, sem criar uma segunda hierarquia de classes.

**Rationale**: essas classes já têm exatamente o formato de uma entidade (campos, construtor
com id via `UUID.randomUUID()`, sem framework acoplado hoje). Duplicar em uma classe
"Entity" separada só para persistência adicionaria indireção sem benefício, contra o
Princípio II (não introduzir abstração além do que a tarefa exige). JPA usa acesso por campo
(anotações nos fields, não nos getters) — não precisa de novos setters além dos que já
existem (`setStatus`).

`@GeneratedValue` não é usado — o id já é atribuído em Java antes de `save()`
(`UUID.randomUUID()` no construtor), então o JPA só precisa saber que é a chave primária
(`@Id`), não gerar um valor novo.

**Alternative considered**: `@GeneratedValue(strategy = GenerationType.UUID)`, deixando o
Hibernate gerar o UUID: rejeitado — mudaria o comportamento observável (o id deixaria de
existir antes do `save()`), sem necessidade, já que o código atual já gera UUIDs
corretamente.

## Decisão 3: Status como `String` (`EnumType.STRING`)

**Decision**: `@Enumerated(EnumType.STRING)` em todos os 3 campos de status.

**Rationale**: `EnumType.ORDINAL` (padrão do JPA se nada for especificado) armazena o índice
numérico do valor no enum — frágil por natureza, e o item 1.5 desta mesma Fase 1 já removeu
valores de todos os 3 enums uma vez (`OrderStatus.COMPLETED`, `PaymentStatus.PENDING`/
`FAILED`, `InvoiceStatus.FAILED`), o que teria corrompido silenciosamente qualquer linha já
gravada com `ORDINAL`. `STRING` grava o nome (`"PENDING"`, `"CONFIRMED"` etc.), imune a
reordenação/remoção de valores.

## Decisão 4: Repository = `JpaRepository<Entidade, UUID>`, injetado no Service

**Decision**: `OrderRepository extends JpaRepository<Order, UUID>` (idem para os outros 2),
sem métodos customizados — os métodos herdados (`save`, `findById`, `findAll`) já cobrem
tudo que os 3 `Service` precisam hoje. O campo `Map<UUID, T>` de cada `Service` é removido;
o `Repository` passa a ser injetado via construtor.

**Rationale**: é o padrão idiomático do Spring Data JPA — nenhuma query customizada é
necessária, já que não há filtro além de "por id" e "todos".

**Efeito colateral (esperado e correto)**: os testes de `Service` do item 1.6
(`OrderServiceTest` etc.), que hoje fazem `new OrderService()` sem argumentos, deixam de
compilar assim que o construtor passa a exigir um `Repository`. O próprio item 1.6 já
documentou essa dependência futura ("Mockito só passa a fazer sentido quando a persistência
real... introduzir um Repository injetado") — agora é o momento de usá-la:
`@Mock OrderRepository` + `@InjectMocks OrderService` (Mockito, já disponível desde o item
1.1), substituindo a instanciação direta.

## Decisão 5: `application.properties` aponta para `localhost:5432`

**Decision**: cada serviço ativa `spring.datasource.url=jdbc:postgresql://localhost:5432/<banco>`,
`spring.datasource.username=<usuário>`, `spring.datasource.password=<senha>` (valores do
item 1.9), mais `spring.jpa.hibernate.ddl-auto=validate` e
`spring.flyway.enabled=true` (padrão, mas explícito por clareza).

**Rationale**: `localhost:5432` é a porta que o `docker-compose.yml` já expõe pro host (item
1.9: `ports: - "5432:5432"` em `postgres-api`). Isso cobre o caso de "rodar o serviço
localmente com `mvnw spring-boot:run`, apontando pro Postgres do Compose" — o mesmo padrão
que já era usado (implicitamente, via propriedades comentadas) antes desta feature. Quando o
serviço roda **dentro** de um container (Compose ou k8s), as variáveis de ambiente
`SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` (já configuradas no item 1.9, apontando pro
hostname interno `postgres-api`) sobrescrevem essas propriedades automaticamente — nenhuma
mudança adicional de infra é necessária nesta feature.

## Decisão 6: Lacuna de teste aceita — Testcontainers fica para o item 1.11

**Decision**: não introduzir Testcontainers nesta feature. Testes `@SpringBootTest` (item
1.8) passam a exigir um Postgres real em `localhost:5432` (`docker compose up -d postgres-api`)
para passar.

**Rationale**: o item 1.11 do ROADMAP já está desenhado especificamente para isso
("Testes de integração com Testcontainers (Postgres)"), logo em seguida a este. Misturar os
dois escopos infringiria a organização já estabelecida do ROADMAP sem necessidade — a lacuna
é curta (o próximo item já resolve) e está documentada explicitamente na spec (FR-008), não
escondida.

**Nesta sessão de trabalho**: o Postgres do item 1.9 já está rodando via `docker compose up
-d postgres-api` (confirmado antes desta spec), então os testes podem ser validados de
verdade nesta mesma sessão, sem esperar o item 1.11.

## Conclusão

Por serviço: 1 `pom.xml` editado (4 dependências: jpa, postgresql, flyway-core,
flyway-database-postgresql), 1 classe Model anotada (`@Entity`), 1 `Repository` novo, 1
migração Flyway nova, `application.properties` editado, 1 `Service` editado (Repository
injetado, `Map` removido), 1 classe de teste de `Service` editada (Mockito). Nenhuma mudança
de contrato HTTP (Controllers/`GlobalExceptionHandler`/DTOs permanecem como estão).
