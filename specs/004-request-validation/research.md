# Research: Validação de Request (Bean Validation)

## Decisão 1: Dependência de validação

**Decision**: adicionar `spring-boot-starter-validation` (versão gerenciada pelo parent
`4.1.1`) aos 3 `pom.xml`.

**Rationale**: já confirmado empiricamente antes desta spec —
`./mvnw dependency:get -Dartifact=org.springframework.boot:spring-boot-starter-validation:4.1.1`
resolveu com sucesso a partir de `order-api` (mesmo BOM dos outros 2 serviços). Traz a API
Jakarta Bean Validation (`jakarta.validation.constraints.*`) + Hibernate Validator como
implementação de referência.

## Decisão 2: Anotações por campo

**Decision**:

| DTO | Campo | Anotações |
|---|---|---|
| `OrderRequest` | `customerId` | `@NotBlank` |
| `OrderRequest` | `amount` | `@NotNull @Positive` |
| `PaymentReservationRequest` | `orderId` | `@NotBlank @Pattern(regexp = UUID_REGEX)` |
| `PaymentReservationRequest` | `amount` | `@NotNull @Positive` |
| `InvoiceRequest` | `orderId` | `@NotBlank @Pattern(regexp = UUID_REGEX)` |
| `InvoiceRequest` | `paymentId` | `@NotBlank @Pattern(regexp = UUID_REGEX)` |
| `InvoiceRequest` | `amount` | `@NotNull @Positive` |

`UUID_REGEX` = `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`
(formato padrão de `java.util.UUID.toString()`).

**Rationale**: `@Positive` é aplicável a `BigDecimal` (a anotação Jakarta Validation cobre
qualquer `Number`), então cobre `amount` sem conversão extra. `@Pattern` mantém
`orderId`/`paymentId` como `String` (decisão já registrada na spec) enquanto garante que só
uma string com a forma de um UUID passe — sem exigir troca de tipo.

**Alternatives considered**:
- Trocar o tipo do campo para `java.util.UUID` diretamente: rejeitado nesta feature (ver
  Assumptions da spec) — mudaria `Model`/`Response` também, fora do escopo de "validação de
  request"; fica associado à introdução de persistência real (itens 1.9/1.10).
- `@UUID` de bibliotecas de terceiros (ex.: Hibernate Validator tem `@UUID` desde certas
  versões): não usado para não introduzir dependência de uma anotação não-padrão quando
  `@Pattern` com Jakarta puro já resolve.

## Decisão 3: Tratamento de `MethodArgumentNotValidException` → 400

**Decision**: adicionar, ao `GlobalExceptionHandler` já existente de cada serviço (criado no
item 1.3), um novo `@ExceptionHandler(MethodArgumentNotValidException.class)` retornando 400
com o `ErrorResponse` estendido (ver Decisão 4).

**Rationale**: `MethodArgumentNotValidException` é a exceção padrão que o Spring MVC lança
quando `@Valid` falha num `@RequestBody` — capturá-la no mesmo `@RestControllerAdvice` que já
trata o 404 mantém um único ponto de tratamento de erro por serviço (coerente com o que o
item 1.3 já estabeleceu), em vez de criar um segundo mecanismo paralelo.

## Decisão 4: Estender `ErrorResponse` (não duplicar)

**Decision**: adicionar um 6º componente `List<String> validationErrors` ao `record
ErrorResponse` de cada serviço, com um construtor secundário de 5 argumentos que preserva o
uso atual (404, sem detalhe de campo) passando `null` para `validationErrors`.

```java
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path, List<String> validationErrors) {
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}
```

Cada item de `validationErrors` no formato `"<campo>: <motivo>"` (ex.:
`"customerId: não deve estar em branco"`), extraído de
`BindingResult#getFieldErrors()`.

**Rationale**: evita duplicar um segundo formato de erro para o mesmo serviço — o contrato
de erro (`contracts/error-404.md` do item 1.3) já é a referência que os consumidores das 3
APIs passam a conhecer; estendê-lo de forma compatível (campo novo, opcional) é menos
surpreendente do que introduzir um formato `400` totalmente diferente do `404`.

**Alternatives considered**:
- Criar um `ValidationErrorResponse` separado: rejeitado — duplicaria os 5 campos já
  existentes (`timestamp`/`status`/`error`/`message`/`path`) só para adicionar 1 novo,
  forçando o cliente a lidar com 2 formatos de erro por serviço em vez de 1.

## Conclusão

Por serviço: editar `pom.xml` (+1 dependência), 1 DTO (`*Request.java`, anotações),
`ErrorResponse.java` (+1 componente, +1 construtor), `GlobalExceptionHandler.java` (+1
`@ExceptionHandler`), e 1 controller (`@Valid` no parâmetro `@RequestBody` do método de
criação). Nenhum `Model`/`*Response.java` é alterado.
