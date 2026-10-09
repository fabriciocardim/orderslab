# Research: Máquina de Estados / Guardas de Transição

## Decisão 1: Onde vive a guarda

**Decision**: a checagem de estado MUST viver no `Service` de cada recurso (dentro de
`confirm()`/`cancel()`/`issue()`), lançando uma nova exceção
`InvalidStatusTransitionException` quando o estado atual não permite a transição pedida —
mesma camada onde `findOrThrow` já lança `*NotFoundException` hoje.

**Rationale**: mantém a regra de negócio (o que é uma transição válida) no Service, não no
Controller nem no `GlobalExceptionHandler` — consistente com o padrão já estabelecido pelos
itens 1.3/1.4 (Controller fica fino, Service decide, `GlobalExceptionHandler` só traduz
exceção → HTTP).

## Decisão 2: Nova exceção por serviço

**Decision**: `InvalidStatusTransitionException extends RuntimeException`, com um construtor
recebendo o estado atual e a ação tentada (ex.:
`new InvalidStatusTransitionException(order.getStatus(), "confirmar")`), montando a mensagem
internamente. Implementada de forma independente e idêntica nos 3 serviços — mesmo nome de
classe, sem compartilhamento de código (Princípio I), mesmo padrão de
`ErrorResponse`/`GlobalExceptionHandler` já usado.

**Alternatives considered**: reaproveitar `IllegalStateException` (do próprio JDK) em vez de
uma exceção própria — rejeitado porque um `@ExceptionHandler(IllegalStateException.class)`
capturaria qualquer uso futuro de `IllegalStateException` no serviço por engano, não só a
transição — o mesmo motivo pelo qual o item 1.3 rejeitou capturar `RuntimeException`
genericamente.

## Decisão 3: Handler HTTP (409)

**Decision**: adicionar, ao `GlobalExceptionHandler` já existente (itens 1.3/1.4) de cada
serviço, um `@ExceptionHandler(InvalidStatusTransitionException.class)` retornando 409 com o
`ErrorResponse` já existente (`error="Conflict"`, `message` da exceção,
`validationErrors=null` — esse campo é específico de erro de validação de request, não se
aplica aqui).

**Rationale**: terceiro `@ExceptionHandler` no mesmo `@RestControllerAdvice` que já trata
404 (item 1.3) e 400 (item 1.4) — um único ponto de tratamento de erro por serviço, como já
estabelecido.

## Decisão 4: Remoção dos valores de enum mortos

**Decision** (já confirmada com o responsável do laboratório antes desta spec):

| Enum | Remover | Motivo |
|---|---|---|
| `OrderStatus` | `COMPLETED` | sem gatilho síncrono possível antes da Fase 2 (Kafka) |
| `PaymentStatus` | `PENDING` | nunca observável — `reserve()` sempre sobrescreve pra `RESERVED` antes de qualquer resposta |
| `PaymentStatus` | `FAILED` | sem regra de negócio simulada nesta feature |
| `InvoiceStatus` | `FAILED` | sem regra de negócio simulada nesta feature |

Para `PaymentStatus.PENDING`: o construtor de `Payment(String orderId, BigDecimal amount)`
passa a setar `status = PaymentStatus.RESERVED` diretamente; `PaymentService.reserve()` para
de chamar `setStatus(RESERVED)` explicitamente logo após construir (fica redundante).

**Rationale**: aplicar o mesmo critério já usado nos itens 1.1/1.2 (não manter
configuração/estado sem uso real "por via das dúvidas") — decisão já tomada antes desta
spec, não é uma escolha de implementação em aberto.

## Conclusão

Máquina de estados resultante (idêntica em forma nos 3 serviços, valores independentes):

```text
order-api:    PENDING  --confirm()--> CONFIRMED (terminal)
              PENDING  --cancel()--> CANCELLED (terminal)

payment-api:  RESERVED --confirm()--> CONFIRMED (terminal)
              RESERVED --cancel()--> CANCELLED (terminal)

invoice-api:  PENDING  --issue()--> ISSUED (terminal)
              PENDING  --cancel()--> CANCELLED (terminal)
```

Qualquer chamada de `confirm`/`cancel`/`issue` fora do estado inicial → `409`.
