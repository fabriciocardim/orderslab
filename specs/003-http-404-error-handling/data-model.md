# Data Model: Tratamento de Erro HTTP — 404 Real

## ErrorResponse

Representa o corpo de uma resposta de erro HTTP. Duplicado de forma independente em cada um
dos 3 serviços (`order-api`, `payment-api`, `invoice-api`) — mesmo formato, sem
compartilhamento de código, conforme Princípio I da constitution.

| Campo | Tipo | Descrição | Regra |
|---|---|---|---|
| `timestamp` | `Instant` | Momento em que o erro ocorreu | MUST ser preenchido no momento do tratamento (`Instant.now()`), serializado como ISO-8601 |
| `status` | `int` | Código de status HTTP | MUST ser `404` para o caso desta feature |
| `error` | `String` | Descrição curta do status | MUST ser `"Not Found"` para o caso desta feature |
| `message` | `String` | Mensagem descritiva do erro | MUST ser exatamente a mensagem já carregada pela exceção `*NotFoundException` (ex.: `"Pedido não encontrado: <id>"`) — sem informação adicional |
| `path` | `String` | Caminho da requisição que originou o erro | MUST ser o path relativo (ex.: `/api/orders/<uuid>`), obtido via `HttpServletRequest#getRequestURI()` — não inclui host/porta |

Não há relacionamento com outras entidades — é um DTO de resposta, não uma entidade
persistida. Não há transições de estado (cada instância é criada e serializada uma única vez
por requisição com erro).
