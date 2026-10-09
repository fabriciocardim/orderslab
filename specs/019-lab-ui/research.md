# Research: UI do Laboratório

Verificações de 2026-10-05 em Node 26.10.0 / npm 11.19.1, com um **projeto-sonda descartável** (`/tmp/uiprobe`) que
instalou e executou a cadeia de ferramentas inteira, uma sonda de nginx em contêiner e a inspeção do bundle do Kafbat.
As versões atuais dos pacotes são bem mais novas do que o esperado — nada abaixo foi presumido. Pendências que
dependem da stack real estão na Decisão 10.

## Decisão 1: Stack e versões (verificadas juntas)

**Decision**: React **19.3.0**, Vite **8.3.2** com `@vitejs/plugin-react` **6.1.2**, TypeScript **~6.0.0 (6.0.3)**,
Vitest **5.0.3** com jsdom **30.1.2**, Testing Library (`react` **16.3.3**, `jest-dom` **7.0.1**, `user-event` **14.6.7**),
MSW **3.0.2**, `@tanstack/react-query` **5.104.1**, ESLint **10.12.0** (flat config) com `typescript-eslint` **8.71.0**,
`eslint-plugin-react-hooks` **7.1.1** e `@eslint/js` **10**. Node **≥ 22.12** (Vite exige `^20.19 || >=22.12`; Vitest
`^22.12 || ^24 || >=26`): imagem de build `node:22-alpine`, CI com Node 22.

**Rationale (evidência da sonda)**: `npm install` falha com `ERESOLVE` se o TypeScript for o **7.0.2** (último), porque
`typescript-eslint@8.71.0` declara `typescript >=4.8.4 <6.1.0`; com `typescript@~6.0.0` tudo resolve. Com essa
combinação a sonda rodou: `tsc --noEmit` limpo, **Vitest 5 passou 2 testes** (componente + react-query + MSW + Testing
Library em jsdom), `eslint .` limpo e `vite build` gerou `dist/` (253 kB, 78 kB gzip).

**Mudança de API achada pela sonda (MSW 3)**: `server.listen({ onUnhandledRequest: 'error' })` não existe mais; a opção
é **`onUnhandledFrame: 'error'`** (`SharedOptions` em `msw/lib/_chunks/shared-options.d.ts`). Handlers continuam
`http.get(...)`/`HttpResponse`, e `setupServer` vem de `msw/node`.

**Alternatives considered**: TypeScript 7 — rejeitado enquanto o `typescript-eslint` não o suportar (lint é gate de CI);
Jest — rejeitado: Vitest já integra com o Vite; Redux/Zustand — rejeitado: o estado é só servidor (react-query) e UI local.

## Decisão 2: nginx — proxy por prefixo, DNS tardio, `config.js` e log JSON (verificado)

**Decision**: imagem `nginx:alpine` com template `default.conf.template` (processado por `envsubst` no start) e um script
`/docker-entrypoint.d/40-config-js.sh` que gera `config.js` a partir de variáveis de ambiente.
- `location /api/orders|payments|invoices` com `set $u ${..._API_URL}; proxy_pass $u;` — **variável no `proxy_pass`** faz o
  nginx resolver o DNS **na requisição**, não no start, usando `resolver ${NGINX_LOCAL_RESOLVERS}` (preenchida por
  `NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1` a partir do `/etc/resolv.conf`: DNS embutido do Docker ou kube-dns).
- `location = /health/{order,payment,invoice}` com `rewrite ^ /actuator/health break;` para o `/actuator/health` do serviço.
- `location / { try_files $uri /index.html; }` (fallback da SPA); `log_format json escape=json` em `/dev/stdout`.
- Timeouts de proxy de 3 s (`proxy_connect_timeout`/`proxy_read_timeout`).

**Rationale (evidência)**: sonda em contêiner com upstream falso: `/config.js` saiu com a URL injetada; `/api/orders`
chegou ao upstream com o caminho intacto; `/health/order` chegou como `/actuator/health`; um upstream **inexistente**
(`servico-que-nao-existe`) devolveu **502 sem derrubar o nginx** (com `proxy_pass` literal o nginx nem subiria se o
serviço ainda não existisse — risco real em Compose/k8s); SPA com fallback; log de acesso em JSON válido com `status`,
`upstream` e tempo.

**Alternatives considered**: `proxy_pass` literal com `upstream {}` — rejeitado (resolve DNS no start, quebra a subida
quando o serviço ainda não está pronto); Nginx unit/Caddy/Traefik — rejeitado: a imagem nginx já tem `envsubst` e é
o que o enunciado pede.

## Decisão 3: Links do Kafbat (verificado no código da ferramenta)

**Decision**: `kafbatUrl` e `kafbatCluster` vêm do `config.js` em runtime (padrões `http://localhost:8090` e
`orderslab`, o nome do cluster em `KAFKA_CLUSTERS_0_NAME` no Compose e no k8s). Link do tópico:
`{kafbatUrl}/ui/clusters/{cluster}/all-topics/{tópico}`; aba de mensagens:
`.../all-topics/{tópico}/messages`.

**Rationale (evidência)**: o bundle do Kafbat v1.5.0 (`/assets/index-*.js`) define `P0="all-topics"`,
`bo=(cluster)=>`${/ui/clusters/{cluster}}/all-topics`` e `R1=(cluster,topic)=>`${bo(cluster)}/${topic}``, e as abas
`messages`, `consumer-groups`, `settings`; `/api/clusters` devolve o nome `orderslab`. O Kafbat não filtra por `orderId`
via URL, então o link leva ao tópico (o filtro por chave é feito na tela dele).

## Decisão 4: Derivação das etapas — função pura, sem I/O

**Decision**: `deriveFlow(order, payments, invoices)` em `src/domain/flow.ts`, determinística e testada por tabela:

| Etapa | feito | falhou | aguardando | não se aplica |
|---|---|---|---|---|
| **pedido** | `order.status` existe (`PENDING`/`CONFIRMED`/`CANCELLED`) | — | pedido ainda não carregou | — |
| **pagamento** | `RESERVED` ou `CONFIRMED` (ou `CANCELLED` por REST: segue "feito") | `FAILED` | pedido existe e não há pagamento do `orderId` | pedido `CANCELLED` sem pagamento criado |
| **nota** | `ISSUED` (ou `PENDING` criada manualmente: "aguardando") | `FAILED` | pagamento feito e não há nota do `orderId` | pagamento `FAILED` (ou ausente e pedido cancelado) |

Estados dos serviços (verificados no código): `OrderStatus {PENDING, CONFIRMED, CANCELLED}`;
`PaymentStatus {RESERVED, CONFIRMED, CANCELLED, FAILED}`; `InvoiceStatus {PENDING, ISSUED, CANCELLED, FAILED}`.
`CANCELLED` de pagamento/nota por ação REST manual é exibido como "cancelado" (estado terminal distinto de "falhou").
Etapa **final** = nenhuma etapa em "aguardando". Várias linhas para o mesmo `orderId` (pagamentos/notas criados por REST):
usa a mais recente por `createdAt`.

**Rationale**: toda a regra fica numa função testável sem React; o componente só renderiza.

## Decisão 5: Polling, timeout e parada (TanStack Query)

**Decision**: `useQuery` com `refetchInterval` **função** que devolve `2000` enquanto o pedido observado não é final e
o contador "sem mudança" < ~30 s, e `false` depois (a aba "Listagens" usa `2000` só enquanto está montada/visível). O
timeout de 3 s é `AbortSignal.timeout(3000)` em cada `fetch`. Em erro, o react-query **mantém o último `data`**;
o painel mostra "indisponível" + último dado. `retry: false` (a próxima rodada do polling é o retry); `staleTime: 0`.
A parada por inatividade usa um hook `useNoChangeTimeout` que compara um *fingerprint* (JSON dos status) entre ciclos.

**Rationale**: atende FR-006/FR-011 sem timers manuais espalhados; "aguardando" fica visível sem spinner infinito.

## Decisão 6: Saúde — HTTP 503 com corpo `DOWN` também é "fora do ar"

**Decision**: `GET /health/{svc}` → UP se `200` com `status: "UP"`; qualquer outra coisa (503 com `{"status":"DOWN"}`, 502/504
do proxy, timeout, rede) → "fora do ar". Corpo observado: `{"groups":["liveness","readiness"],"status":"UP"}`;
`management.endpoints.web.exposure.include=health,metrics` já expõe `/actuator/health` nos 3 serviços.

## Decisão 7: Destino do proxy por ambiente (variáveis)

| Ambiente | `ORDER_API_URL` | `PAYMENT_API_URL` | `INVOICE_API_URL` | Observação |
|---|---|---|---|---|
| Docker Compose | `http://order-service:8080` | `http://payment-service:8080` | `http://invoice-service:8080` | porta interna dos contêineres |
| k8s (namespace `orderslab`) | `http://order-service:8081` | `http://payment-service:8082` | `http://invoice-service:8083` | porta do **Service** (→ 8080), como em `infra/k8s/*-service-service.yaml` |
| Dev (Vite) | `http://localhost:8081` | `http://localhost:8082` | `http://localhost:8083` | `server.proxy` no `vite.config.ts`, serviços via `spring-boot:run` |

`KAFBAT_URL` (padrão `http://localhost:8090`) e `KAFBAT_CLUSTER` (padrão `orderslab`) para o `config.js`. No k8s o
Kafbat é um Service `LoadBalancer :8090`; o link abre no navegador, então precisa de port-forward/ingress (documentado).

## Decisão 8: CI do front (Princípio — pipeline próprio) e imagem

**Decision**: `frontend-ci.yml` (reutilizável, `workflow_call`) com jobs `build-and-test` (Node 22, `npm ci`, `npm run lint`,
`npm test`, `npm run build`), `security` (CodeQL com `languages: javascript-typescript`, `build-mode: none`) e `package`
(`needs` dos dois, só em tags `frontend-react-v*.*.*`, `docker build`), e o gatilho fino `frontend-react.yml` espelhando
`order-api.yml` (paths `frontend-react/**`). **Dockerfile** multi-stage: `node:22-alpine` (`npm ci` + `npm run build`) →
`nginx:alpine` (copia `dist/`, o template e o script `40-config-js.sh`; `ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1`).

**Rationale**: o `service-ci.yml` é de Maven (JDK, PMD, CodeQL de Java) e não se aplica; manter o padrão de "workflow
reutilizável + gatilho fino" respeita a governança sem forçar Java no front.

## Decisão 9: Observabilidade (Princípio VI, decisão explícita)

Log de acesso do nginx em JSON (campos: tempo, método, uri, status, bytes, tempo da requisição, upstream) e **sem
OpenTelemetry de navegador** na v1. A UI não propaga `traceparent`; o tracing distribuído é da Fase 6.

## Decisão 10: Pendências a validar na implementação (stack real)

1. `npm ci`/`lint`/`test`/`build` verdes no projeto real (as versões da Decisão 1 juntas).
2. Os 5 cenários contra os 3 serviços reais (via `vite` com proxy): estados finais corretos; `exatamente 500`/`1000` dentro.
3. Serviço parado: só o painel dele fica "indisponível"; polling para; nada em branco.
4. Imagem nginx real: `docker build`; contêiner com env de Compose; `config.js`, proxy, `/health/*`, 502 com upstream
   inexistente, log JSON; links do Kafbat com o cluster `orderslab`.
5. Compose e k8s: manifests consistentes com o padrão (validação por `docker compose config` e `kubectl --dry-run=client`
   se disponível).
6. Os 9 links do cartão de tópicos batem com as páginas do Kafbat (checagem contra o Kafbat real com o Kafka do Compose).

## Riscos e limitações conhecidas

- **Listas sem filtro nem paginação**: cada ciclo baixa a lista inteira (limitação do enunciado; mostrar só 200).
- **Pedido fica `PENDING` até confirmação manual** (a cadeia assíncrona não confirma o pedido): o acompanhamento mostra
  o pedido "feito" mesmo assim (criado), e a confirmação é uma ação do usuário.
- **Motivo da falha** só no Kafbat (o REST não expõe).
- **Versões muito novas** de toda a cadeia (React 19.3, Vite 8, Vitest 5, MSW 3, ESLint 10): fixadas em `package-lock.json`;
  TypeScript preso em 6.0 até o `typescript-eslint` suportar 7.
- **Máquina de 8 GB**: build/test do front são leves, mas a validação empírica não deve subir os 3 serviços em contêineres
  ao mesmo tempo (rodar via `spring-boot:run` + Compose só de Kafka/Postgres).

## Validações empíricas (implementação, 2026-10-05)

Node 26.10.0 / npm 11.19.1; Postgres, Kafka e Kafbat do Compose; os 3 serviços por `spring-boot:run` (8081–8083); a UI pelo
Vite (`:5173`) com o proxy de `vite.config.ts`. Pendências da Decisão 10:

1. **Gates do front** — `npm run lint` limpo, **59 testes** (Vitest 5 + MSW 3 + Testing Library) verdes, `tsc` limpo e
   `vite build` ok (268 kB, 83 kB gzip); ~26 s no total (SC-007: < 2 min). ✅
2. **Os 5 cenários** (pelo proxy do Vite, mesmo polling de 2 s e a `deriveFlow` real sobre as respostas reais):
   10.50 → feito·feito·feito (3,3 s); 500 → feito·feito·feito (3,5 s); 750 → feito·feito·**falhou** (5,2 s); 1000 →
   feito·feito·**falhou** (4,9 s); 1500 → feito·**falhou**·não se aplica (3,1 s). Todos finais e dentro de 15 s (SC-001/SC-002). ✅
3. **Ações e erros** — confirmar → 200 `CONFIRMED`; cancelar depois de confirmado → **409**; pedido inexistente → **404**;
   cliente vazio/valor negativo → **400** com `validationErrors` (`customerId: …`, `amount: …`); listas e saúde → 200. ✅
4. **Degradação** — `payment-api` parado: `/health/payment` e `/api/payments` → **502** e o resto segue **200**; Postgres
   parado: `/health/invoice` → **503** `{"status":"DOWN"}` e a saúde do `order-api` estourou o timeout (para a UI, 3 s, ambos
   "fora do ar"); ao voltar o banco, `UP`. ✅
5. **Imagem nginx real** (`docker build`, 93 MB) — `config.js` gerado no start com o env, `Cache-Control: no-store`;
   `/api/orders` e `/api/payments` com o caminho preservado; `/health/order` → `/actuator/health`; upstream inexistente →
   **502** sem derrubar o contêiner; rota da SPA → 200; log de acesso em JSON. `docker compose config` ok; k8s com
   `kubectl apply --dry-run=client --validate=false` ok. ✅
6. **Links do Kafbat** — cluster `orderslab` ONLINE; os **9** tópicos (7 de evento + 2 DLTs) existem na API do Kafbat e as
   páginas `…/all-topics/<tópico>` e `…/messages` respondem 200; a troca de `KAFBAT_URL`/`KAFBAT_CLUSTER` sem recompilar foi
   comprovada na imagem real. ✅

**Não verificado:** a renderização visual em um **navegador real**. A extensão do Chrome não estava conectada; o
comportamento da tela é coberto por testes de componente em jsdom (formulário, cenários, acompanhamento com relógio falso,
listas, saúde, tópicos), e o caminho de dados foi validado contra a stack real. O layout/estilo deve ser conferido abrindo
`http://localhost:3000` (Compose) ou `http://localhost:5173` (`npm run dev`).

## Achados de implementação

- **TypeScript preso em 6.0** por causa do `typescript-eslint`; **MSW 3**: `onUnhandledFrame` no `server.listen`.
- **Vitest em máquina de 8 GB**: com vários workers jsdom e o host em swap, testes que levam ms estouraram 5 s; fixados
  `maxWorkers: 2` e `testTimeout: 15 s` (59 testes em ~17 s).
- **react-query + relógio falso**: a notificação do erro do refetch só chega com mais alguns ms depois do intervalo
  (o teste avança 10 ms a mais); `findBy*` não funciona com relógio falso (usar `act` + `advanceTimersByTimeAsync`).
- **Polling com parada**: `refetchInterval` não enxerga o estado derivado dos próprios dados; `fingerprint` e `terminal`
  viram estado do React atualizado pelo `queryFn` (callback assíncrono), e `useNoChangeTimeout` deriva o "parou" de uma
  chave (sem `setState` síncrono em efeito, regra do `eslint-plugin-react-hooks` 7).
- **k8s vs Compose**: portas dos Services (`8081/8082/8083`) diferem da porta interna dos contêineres (`8080`), por isso os
  destinos do proxy são variáveis.
