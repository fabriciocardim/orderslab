# Research: Testes de Integração com Testcontainers

## Decisão 1: Artifacts corretos para Spring Boot 4.1.1 + Testcontainers 2.x

**Decision**: adicionar, em escopo `test`, aos 3 `pom.xml`:
- `org.springframework.boot:spring-boot-testcontainers` (módulo direto, **não** é um
  `-starter-`, sem `<version>` — gerenciado pelo `spring-boot-dependencies:4.1.1`)
- `org.testcontainers:testcontainers-postgresql` (**não** `postgresql`)
- `org.testcontainers:testcontainers-junit-jupiter` (**não** `junit-jupiter`)

**Rationale**: confirmado via `dependency:get` e inspeção do `testcontainers-bom-2.0.5.pom`
(importado pelo `spring-boot-dependencies:4.1.1`, que fixa `testcontainers.version=2.0.5`).
Testcontainers 2.x **renomeou todos os módulos de banco/integração com o prefixo
`testcontainers-`** (`postgresql` → `testcontainers-postgresql`,
`junit-jupiter` → `testcontainers-junit-jupiter`, etc.) — os nomes "clássicos" sem prefixo
(usados em Testcontainers 1.x e em praticamente todo tutorial/exemplo pré-2.x) **não
resolvem mais**: `mvnw dependency:get -Dartifact=org.testcontainers:postgresql:2.0.5` falha
com `Could not find artifact`. Terceira surpresa de nomenclatura de artifact nesta sessão
(depois de `-webmvc`/`-kafka` no item 1.1 e `spring-boot-starter-flyway` no item 1.10) — desta
vez originada na própria biblioteca Testcontainers, não no Spring Boot.

`spring-boot-testcontainers`, ao contrário do Flyway (item 1.10), é um módulo direto sem
`-starter-` — consistente com a documentação oficial do Spring Boot, que sempre apresentou
esse módulo assim (confirmado via inspeção do BOM: não existe nenhum
`spring-boot-starter-testcontainers`).

**Alternatives considered**: assumir os nomes "clássicos" (`org.testcontainers:postgresql`,
`org.testcontainers:junit-jupiter`) por familiaridade com Testcontainers 1.x — rejeitado após
falha real de resolução; confirmado empiricamente antes de escrever qualquer código, seguindo
o padrão já estabelecido nesta sessão.

## Decisão 2: `@ServiceConnection` não mudou de pacote

**Decision**: usar `org.springframework.boot.testcontainers.service.connection.ServiceConnection`
— mesmo pacote usado desde a introdução da anotação (Spring Boot 3.1).

**Rationale**: confirmado via `unzip -l` no jar `spring-boot-testcontainers-4.1.1.jar` — a
classe `ServiceConnection` está exatamente nesse pacote, sem alteração. Diferente de
`@WebMvcTest`/`@MockitoBean` (que mudaram de pacote em Boot 4, achado do item 1.7), este
módulo manteve compatibilidade total de pacote.

## Decisão 3: `PostgreSQLContainer` — usar o pacote novo, sem generics

**Decision**: importar `org.testcontainers.postgresql.PostgreSQLContainer` (pacote novo do
Testcontainers 2.x), não `org.testcontainers.containers.PostgreSQLContainer` (pacote
"clássico", mantido só por compatibilidade). Declarar o campo como `PostgreSQLContainer`
puro, sem parâmetro de tipo (`<?>`/`<SELF>`) — a classe nova não é mais genérica.

**Rationale**: confirmado via `javap` que o jar `testcontainers-postgresql-2.0.5.jar` contém
**as duas** versões da classe: a clássica `org.testcontainers.containers.PostgreSQLContainer
<SELF extends PostgreSQLContainer<SELF>>` (API antiga, com self-type genérico, mantida para
não quebrar código legado) e a nova `org.testcontainers.postgresql.PostgreSQLContainer`
(sem generics, API simplificada — reflete a limpeza geral de API do Testcontainers 2.x). A
classe nova não tem construtor sem argumento — exige passar a imagem explicitamente
(`new PostgreSQLContainer("postgres:15-alpine")`), o que é desejável aqui: obriga a fixar a
mesma imagem/versão já usada em `infra/docker-compose.yml` (FR-004 da spec), em vez de
depender de uma tag `latest` implícita.

**Alternatives considered**: usar o pacote clássico `org.testcontainers.containers.
PostgreSQLContainer<?>` — funcionaria (mantido por compatibilidade), mas optar pelo pacote
novo evita dívida técnica desnecessária logo na primeira vez que este projeto usa
Testcontainers, e a ausência de generics deixa o código mais limpo.

## Decisão 4: `@Testcontainers` + `@Container` (JUnit 5) para gerenciar o ciclo de vida

**Decision**: anotar a classe de teste com `@Testcontainers` (extensão JUnit 5) e o campo do
container com `@Container` (start/stop automático) + `@ServiceConnection` (injeta
`spring.datasource.*` automaticamente no contexto Spring, sem `@DynamicPropertySource`
manual) — ambas de `org.testcontainers.junit.jupiter`, pacote confirmado inalterado.

**Rationale**: é o padrão oficial documentado pelo Spring Boot para testes de integração com
Testcontainers desde a introdução de `@ServiceConnection` — elimina a necessidade de
`@DynamicPropertySource` boilerplate para mapear `datasource.url`/`username`/`password`
manualmente. O container é `static` (compartilhado entre os métodos de teste da mesma
classe, criado uma vez por classe de teste) — inicia antes de todos os testes da classe e é
descartado ao final, satisfazendo FR-007 (cada execução parte de um banco vazio) sem
overhead de subir um container novo por método de teste.

**Alternative considered**: `@EnabledIfDockerAvailable` (nova em Testcontainers 2.x, também
confirmada no jar `testcontainers-junit-jupiter`) — permitiria *pular* os testes de
integração quando Docker não está disponível, em vez de falhar. Rejeitada porque contraria
FR-002/Edge Case da spec: o comportamento esperado quando Docker não está rodando é falha
clara, não pulo silencioso — um `mvnw test`/CI verde por testes pulados sem ninguém perceber
seria pior do que uma falha explícita.

## Decisão 5: escopo — só os `*ApplicationTests.java` (item 1.8) mudam

**Decision**: o container Testcontainers é declarado exclusivamente nas classes
`OrderApiApplicationTests`/`PaymentApiApplicationTests`/`InvoiceApiApplicationTests` (item
1.8) — nenhuma mudança em `*ServiceTest.java` (item 1.6, Mockito puro) nem em
`*ControllerTest.java` (item 1.7, `@WebMvcTest` mockando o Service). `application.properties`
de produção (usado quando o serviço roda de verdade, fora de teste) não é tocado — o
`@ServiceConnection` sobrescreve `spring.datasource.*` só dentro do contexto de teste
Spring, e a mesma migração Flyway do item 1.10 (`V1__create_<tabela>_table.sql`) roda
normalmente contra o container efêmero, sem qualquer ajuste — Flyway não sabe nem precisa
saber que o banco é efêmero.

**Rationale**: consistente com FR-005/FR-008 da spec — a mudança é isolada aos testes que já
dependiam de banco (item 1.8), preservando tudo o mais exatamente como está.

## Conclusão

Por serviço: `pom.xml` editado (3 dependências de teste: `spring-boot-testcontainers`,
`testcontainers-postgresql`, `testcontainers-junit-jupiter`, sem versão explícita — todas
gerenciadas pelo BOM), e o `*ApplicationTests.java` existente (item 1.8) editado para
declarar o container (`@Testcontainers` na classe, `@Container @ServiceConnection static
PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")` como campo) —
nenhum arquivo novo de produção, nenhuma mudança de `application.properties`/migrations/
Service/Controller.
