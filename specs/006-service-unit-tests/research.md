# Research: Testes Unitários de Service

## Decisão 1: Ferramentas de teste

**Decision**: JUnit 5 (Jupiter) + AssertJ para asserções fluentes. Sem Mockito (nada a
mockar hoje — ver Assumptions da spec) e sem `@SpringBootTest`/`@ExtendWith(SpringExtension)`
— instanciação direta do `Service` via `new OrderService()` etc.

**Rationale**: já confirmado via `./mvnw dependency:tree` em `order-api` que
`junit-jupiter`, `assertj-core` e `mockito-core` já estão no classpath de teste
(transitivos de `spring-boot-starter-*-test`, itens 1.1-1.3) — nenhuma dependência nova
necessária. Como os 3 `Service` não recebem nada via construtor (armazenamento é um campo
`Map` criado internamente), não há um colaborador para substituir por mock — um teste JUnit
puro, instanciando o objeto real, já cobre o comportamento por completo.

## Decisão 2: Estrutura de teste (por serviço, idêntica em forma)

**Decision**: uma classe `<Entity>ServiceTest` em
`src/test/java/.../service/`, usando `@BeforeEach` para instanciar um `Service` novo a cada
teste (evita estado vazado entre testes, já que o `Map` interno não é resetável de fora).

Casos cobertos (nomes de método no padrão `should<Resultado>When<Condição>`):

| # | Caso | Verificação |
|---|---|---|
| 1 | `shouldCreateWithInitialStatus` | `create()`/`reserve()` retorna o recurso no estado inicial correto (`PENDING`/`RESERVED`) |
| 2 | `shouldFindByIdWhenExists` | `findById()` retorna o recurso certo |
| 3 | `shouldThrowNotFoundWhenIdDoesNotExist` | `findById()` com id aleatório lança `*NotFoundException` |
| 4 | `shouldThrowNotFoundOnActionWhenIdDoesNotExist` | a ação (`confirm`/`cancel`/`issue`) com id aleatório também lança `*NotFoundException` |
| 5 | `shouldTransitionWhenInInitialState` | a ação a partir do estado inicial muda o status corretamente |
| 6 | `shouldThrowInvalidTransitionWhenNotInInitialState` | a mesma ação, aplicada de novo (recurso já fora do estado inicial), lança `InvalidStatusTransitionException` |
| 7 | `shouldReturnEmptyListWhenNoneCreated` | `findAll()` sem nenhum recurso criado retorna lista vazia |

`payment-api`/`invoice-api` repetem os casos 4-6 para a segunda ação (`cancel` além de
`confirm`/`issue`), já que cada serviço tem 2 ações de transição.

**Rationale**: nomenclatura declarativa (`should...When...`) deixa o motivo da falha óbvio no
relatório do `mvnw test`, sem precisar abrir o corpo do teste. Uma instância nova de
`Service` por teste (via `@BeforeEach`) evita que a ordem de execução dos testes afete o
resultado — importante já que o armazenamento hoje é um `Map` mutável sem reset explícito.

**Alternatives considered**: reaproveitar uma única instância de `Service` para a classe
toda (`@BeforeAll` em vez de `@BeforeEach`): rejeitado — testes deixariam de ser
independentes entre si (um teste que cria 3 pedidos afetaria a contagem de `findAll()` do
próximo teste).

## Conclusão

3 classes de teste novas (uma por serviço), ~7-9 casos cada (7 casos base + 1-2 repetições
para a segunda ação de transição em `payment-api`/`invoice-api`), sem nenhuma mudança em
código de produção.
