# Research: Filtro por Pedido no Polling e Listas Limitadas

## Contexto (evidência do teste de carga T1)

Rodada `load-prod-202610181944` (perfil `load`, 10 pedidos/s, 50 visitantes, 2026-10-18), contra produção:

- `GET /api/payments` e `GET /api/invoices` chegaram a ~780 KB e ~810 KB (~3,6 mil registros); mediana de 3,05 s e
  2,4 s, bem no corte de 3 s do nginx (`proxy_read_timeout`) → 8.646 respostas 504.
- 6 GB baixados em 16 min, quase tudo dessas duas listas; 919 VUs presos esperando.
- A VPS (2 vCPU, 7,75 GB, sem swap) esgotou a memória e precisou de reboot.
- O pipeline assíncrono não foi o problema: 0 desfechos errados em 1.011 fluxos, mediana pedido→nota de 2,8 s.

Origem no código: `useTrackedOrder.loadSnapshot` chama `listPayments()`/`listInvoices()` a cada 2 s e filtra no
navegador; `useServiceList` faz o mesmo na aba Listagens; os serviços respondem com `repository.findAll()`.

## Decisão 1 — Filtro por pedido como query param no recurso existente

**Escolhido**: `GET /api/payments?orderId=<uuid>` (e `/api/invoices?orderId=`) devolvendo **lista**.

**Por quê**: mantém o recurso e o formato de resposta (array de `PaymentResponse`); o front e o k6 continuam
procurando "o mais recente do pedido" sem mudar a lógica de derivação (`deriveFlow`). Lista vazia = "ainda não chegou"
(200), coerente com a etapa "aguardando".

**Alternativas descartadas**: `GET /api/orders/{id}/payment` (atravessaria serviços — Princípio I);
`GET /api/payments/by-order/{orderId}` devolvendo um objeto/404 (mudaria a semântica de "aguardando" para erro e
exigiria tratar 404 como estado normal na UI).

## Decisão 2 — Lista sem filtro limitada por padrão, total em `X-Total-Count`

**Escolhido**: `?limit=` (1–500, padrão 200), ordenado por `created_at` desc; total no cabeçalho `X-Total-Count`.

**Por quê**: o corpo continua um array (sem envelope `{items,total}` que quebraria o contrato atual); a UI já exibe só
os 200 mais recentes (`LIST_LIMIT`) e o aviso de "lista limitada" precisa do total. `findAll(Pageable)` do Spring Data
já faz o `count(*)` junto.

**Mudança de comportamento declarada**: `GET` sem parâmetros deixa de devolver tudo. Consumidores conhecidos: UI e k6.

**Alternativas descartadas**: manter "tudo" como padrão e o front pedir `limit` (deixaria a armadilha aberta para
qualquer outro cliente); paginação completa com `page` (a UI não navega páginas — fora de escopo).

## Decisão 3 — Índices novos (Flyway V3 em cada serviço)

- `payments(order_id)` e `invoices(order_id)`: o índice existente `ux_*_order_id_from_event` é **parcial**
  (`WHERE source_event_id IS NOT NULL`) e o Postgres não o usa para um `WHERE order_id = ?` simples.
- `payments(created_at DESC)`, `invoices(created_at DESC)`, `orders(created_at DESC)`: `ORDER BY created_at DESC LIMIT n`
  sem varrer e ordenar a tabela inteira.

`CREATE INDEX` comum (não `CONCURRENTLY`): roda dentro da transação do Flyway e, no tamanho atual das tabelas, leva
milissegundos.

## Decisão 4 — Validação no formato `ErrorResponse`

O `GlobalExceptionHandler` só trata `MethodArgumentNotValidException` (corpo). Para query params:

- `MethodArgumentTypeMismatchException` (UUID ou `limit` não numérico) → 400 `ErrorResponse` com
  `validationErrors = ["<param>: valor inválido"]`;
- `HandlerMethodValidationException` (validação de método nativa do Spring MVC para `@Min`/`@Max` em `@RequestParam`)
  → 400 `ErrorResponse` com `validationErrors = ["<param>: <mensagem>"]`.

## Decisão 5 — `order_id` é `VARCHAR`

`payments.order_id`/`invoices.order_id` são `VARCHAR` com o UUID em minúsculas (`UUID.toString()`). O controller recebe
`UUID` (validação de formato de graça) e o service consulta com `orderId.toString()` — cobre o caso do id em
maiúsculas, que o Spring converte para o mesmo UUID.

## Decisão 6 — Frontend

- `http.ts`: `requestList<T>()` devolve `{ items, total }` (total do `X-Total-Count`, ou `items.length` se ausente),
  reutilizando timeout/erros de `request()`.
- `useTrackedOrder`: troca para `listPaymentsByOrder`/`listInvoicesByOrder`; `deriveFlow` e o fingerprint não mudam.
- `useServiceList`: fetcher devolve `{ items, total }`; `truncated = total > rows.length`.
- nginx: nada muda (`proxy_pass $upstream;` sem URI repassa a query string; mesma origem, cabeçalho visível ao JS).

## Validações empíricas (implementação)

Executado em 2026-10-19 nesta máquina (Windows 11, Docker Desktop 29.7.2, JDK 25 compilando `--release 21`).

| Verificação | Antes | Depois |
|---|---|---|
| `order-api` `./mvnw clean verify` | 46 testes ✅ | 54 testes ✅ |
| `payment-api` `./mvnw clean verify` | 62 testes ✅ | 74 testes ✅ |
| `invoice-api` `./mvnw clean verify` | 62 testes ✅ | 74 testes ✅ |
| `frontend-react` `npm test` | 59 testes ✅ | 71 testes ✅ |
| `frontend-react` `npm run lint`, `tsc --noEmit`, `npm run build` | — | ✅ |
| `k6 inspect` dos 6 perfis + `smoke` contra mock do contrato novo | — | ✅ (todos os thresholds) |

Os testes novos de integração rodam contra Postgres real (Testcontainers): filtro por pedido (só os registros do
pedido, mais recente primeiro; id em maiúsculas; pedido sem registro → `[]`), limite com `X-Total-Count` igual ao
`count(*)`, `limit=501` → 400 e existência dos índices `ix_*` em `pg_indexes`.

Achados de ambiente (não do código):

- O `JAVA_HOME` da máquina aponta para o JDK 17 → `release version 21 not supported`; usado o JDK 25 de `~/.jdks`.
- Com JDK 25 neste Windows, `Selector.open()` falha com `Unable to establish loopback connection` quando o socket
  Unix-domain do pipe interno é criado no `%TEMP%` do usuário (afeta `HttpClient` do `RestTestClient` e o cliente
  Kafka). Contornado com `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` só na execução dos testes.

Pendente (pós-deploy, SC-005): repetir o perfil `load` do T1 em produção.
