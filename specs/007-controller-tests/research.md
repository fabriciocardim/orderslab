# Research: Testes de Controller (@WebMvcTest)

## Decisão 1: Pacotes das anotações (Spring Boot 4.1.1 / Spring Framework 7)

**Decision**: confirmado via inspeção dos `.jar` em `~/.m2` (mesmo método usado no item 1.1
para os artifact IDs do Maven):

| Classe | Pacote confirmado | Observação |
|---|---|---|
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` | **Mudou** em relação ao Boot 3.x (`...boot.test.autoconfigure.web.servlet...`); vive no jar `spring-boot-webmvc-test` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` | **`@MockBean` foi totalmente removida** — nenhuma classe `MockBean.class` encontrada em nenhum jar do Spring |
| `MockMvc` | `org.springframework.test.web.servlet.MockMvc` | inalterado |
| `MockMvcRequestBuilders` | `org.springframework.test.web.servlet.request.MockMvcRequestBuilders` | inalterado |
| `MockMvcResultMatchers` | `org.springframework.test.web.servlet.result.MockMvcResultMatchers` | inalterado |
| `ObjectMapper` | `tools.jackson.databind.ObjectMapper` (Jackson 3) | **Mudou** — Boot 4.1.1 migrou pra Jackson 3 (`tools.jackson.core:jackson-databind`), pacote totalmente diferente do clássico `com.fasterxml.jackson.databind`. Decisão: não usar `ObjectMapper` nos testes (ver Decisão 3) — evita essa dependência de pacote inteiramente |

**Rationale**: evita o mesmo tipo de suposição errada que o item 1.1 encontrou nos artifact
IDs — Spring Boot 4 reorganizou vários pacotes de teste, mas nem todos; verificar caso a
caso é mais seguro que assumir "igual ao Boot 3.x" ou "tudo mudou".

## Decisão 2: `@WebMvcTest` carrega o `@RestControllerAdvice` automaticamente

**Decision**: nenhuma configuração extra é necessária para o `GlobalExceptionHandler` (item
1.3) estar presente no contexto de teste — `@WebMvcTest` inclui automaticamente todo
`@ControllerAdvice`/`@RestControllerAdvice` do pacote escaneado, independente de qual
controller foi passado como argumento da anotação.

**Rationale**: confirma que os testes desta feature exercitam o handler real (não um
substituto), cumprindo o objetivo da spec de validar a "fiação real da camada MVC".

## Decisão 3: Estrutura de teste (por serviço, idêntica em forma)

**Decision**: uma classe `<Entity>ControllerTest` em `src/test/java/.../controller/`, com:

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean OrderService orderService;
    // corpo de requisição como String literal (JSON simples, 2-3 campos) —
    // evita depender do pacote do ObjectMapper (ver tabela de pacotes acima)
    // ...
}
```

Casos cobertos (nomes no mesmo padrão `should<Resultado>When<Condição>` do item 1.6):

| # | Caso | Verificação |
|---|---|---|
| 1 | `shouldReturn201WhenCreatingWithValidBody` | `Service` mockado retorna sucesso; `status().isCreated()` + corpo |
| 2 | `shouldReturn400WhenCreatingWithInvalidBody` | corpo com campo obrigatório ausente; `status().isBadRequest()` + `jsonPath("$.validationErrors")` não vazio; `verify(service, never())...` — `Service` nunca chamado |
| 3 | `shouldReturn200WhenFindingByIdExists` | `Service` mockado retorna sucesso; `status().isOk()` |
| 4 | `shouldReturn404WhenFindingByIdDoesNotExist` | `Service` mockado lança `*NotFoundException`; `status().isNotFound()` + shape do `ErrorResponse` |
| 5 | `shouldReturn200WhenListingResources` | `Service` mockado retorna lista; `status().isOk()` |
| 6 | `shouldReturn200WhenFirstTransitionIsValid` (×2, uma por ação) | `Service` mockado retorna sucesso; `status().isOk()` |
| 7 | `shouldReturn409WhenTransitionIsBlocked` (×2, uma por ação) | `Service` mockado lança `InvalidStatusTransitionException`; `status().isConflict()` + shape do `ErrorResponse` |

Total: 9 casos por serviço (7 da tabela acima, com os itens 6-7 contando 2× cada por
terem 2 ações).

**Rationale**: mesmo padrão de nomenclatura declarativa do item 1.6, para consistência entre
as duas camadas de teste. Mockar o retorno/exceção do `Service` (em vez de deixá-lo rodar de
verdade) isola o teste na camada MVC — a lógica de negócio já tem sua própria suíte (item
1.6), evitando duplicação de cobertura com propósitos diferentes.

**Alternatives considered**: usar `@SpringBootTest` + `TestRestTemplate` (subindo o contexto
completo, como foi feito manualmente via `curl` nos itens 1.3-1.5): rejeitado para esta
feature — mais lento (sobe todo o contexto Spring) e não isola a camada MVC do resto; fica
reservado para os testes de integração ponta a ponta do item 1.11 (com Testcontainers).

## Conclusão

3 classes de teste novas (uma por serviço), 9 casos cada (27 no total), sem nenhuma mudança
em código de produção. `Service` sempre mockado via `@MockitoBean`.
