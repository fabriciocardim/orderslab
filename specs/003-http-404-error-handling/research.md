# Research: Tratamento de Erro HTTP — 404 Real

## Decisão 1: Mecanismo de tratamento

**Decision**: `@RestControllerAdvice` + `@ExceptionHandler(XxxNotFoundException.class)` por
serviço, retornando `ResponseEntity<ErrorResponse>` com status 404.

**Rationale**: é o mecanismo idiomático do Spring MVC para tratar exceções de forma
centralizada sem tocar em cada controller individualmente; já é mencionado no `Input` da
spec e é o padrão usado por qualquer projeto Spring Boot para esse tipo de caso.

**Alternatives considered**:
- `@ResponseStatus(HttpStatus.NOT_FOUND)` diretamente na classe `*NotFoundException`:
  rejeitado como abordagem única — funciona para o status code, mas não permite controlar o
  corpo da resposta (FR-003 exige um corpo estruturado com 5 campos), então precisaria de
  `@ExceptionHandler` de qualquer forma. Pode ser mantido como redundância opcional, mas não
  substitui o `@RestControllerAdvice`.
- Handler genérico para `RuntimeException`: rejeitado — mascararia bugs reais (erros
  internos passariam a responder 404), conforme já registrado nos Edge Cases da spec.

## Decisão 2: Formato do corpo de erro

**Decision**: um pequeno `record ErrorResponse(Instant timestamp, int status, String error,
String message, String path)`, duplicado (não compartilhado) em cada um dos 3 serviços.

**Rationale**: replica o formato que o próprio Spring Boot já usa por convenção no handler
de erro padrão (`/error`), então é reconhecível por qualquer consumidor familiarizado com
APIs Spring — sem precisar adotar RFC 7807 (`ProblemDetail`) formalmente (decisão já
registrada nas Assumptions da spec). Ser um `record` (Java 21) evita boilerplate de
getters/construtor.

**Alternatives considered**:
- Extrair para uma classe compartilhada entre os 3 serviços (ex.: módulo `common`):
  rejeitado — viola o Princípio I (Independência dos Serviços) da constitution, que exige
  implementação independente mesmo quando o resultado final é idêntico entre os serviços
  (ver FR-004/FR-005 e User Story 3 da spec, que pedem exatamente isso: mesmo formato, zero
  acoplamento).
- `org.springframework.http.ProblemDetail` (RFC 7807, nativo do Spring 6+): rejeitado por
  ora — mudaria o formato do corpo (campos `type`/`title`/`detail`/`instance` em vez de
  `error`/`message`), o que a spec explicitamente decidiu não adotar nesta feature.

## Decisão 3: Como obter o `path` da requisição

**Decision**: injetar `jakarta.servlet.http.HttpServletRequest request` como parâmetro do
método `@ExceptionHandler` e usar `request.getRequestURI()`.

**Rationale**: é a forma mais direta disponível no Spring MVC sem dependências extras; o
projeto já usa `spring-boot-starter-webmvc` (Tomcat embutido), que traz a Servlet API
(`jakarta.servlet.*`) no classpath.

**Alternatives considered**: `WebRequest`/`ServletWebRequest` — funcionalmente equivalente,
mas exige um cast (`((ServletWebRequest) webRequest).getRequest()`) para chegar ao mesmo
resultado; `HttpServletRequest` direto é mais simples e igualmente suportado pelo Spring MVC
na assinatura de métodos `@ExceptionHandler`.

## Conclusão

Para cada um dos 3 serviços, criar independentemente:
- `exception/ErrorResponse.java` (record)
- `exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`, um `@ExceptionHandler`
  mapeando a `*NotFoundException` específica do serviço)

Sem alterar `Order`/`Payment`/`Invoice`Controller, nem as classes `*NotFoundException`
existentes (continuam simples, sem `@ResponseStatus` — o mapeamento fica só no handler).
