---

description: "Task list — UI do laboratório (U1)"
---

# Tasks: UI do Laboratório

**Input**: Design documents from `/specs/019-lab-ui/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUÍDOS — FR-016 exige testes da derivação das etapas, do cliente das APIs (MSW) e do componente de acompanhamento, além de lint, tipos e build.

**Organization**: por user story (US1 = criar pedido e ver o fluxo; US2 = acompanhamento claro com parada; US3 = listas, saúde e tópicos), mais uma fase de implantação/CI e a validação empírica.

## Format: `[ID] [P?] [Story] Description`

Caminhos relativos à raiz do repo; `<F>` = `frontend-react/`. Versões **fixadas pela sonda** (research.md, Decisão 1): React 19.3.0, Vite 8.3.2, `@vitejs/plugin-react` 6.1.2, **TypeScript ~6.0.0**, Vitest 5.0.3, jsdom 30.1.2, Testing Library (`react` 16.3.3, `jest-dom` 7.0.1, `user-event` 14.6.7), **MSW 3.0.2** (opção `onUnhandledFrame`, não `onUnhandledRequest`), `@tanstack/react-query` 5.104.1, ESLint 10.12 + `typescript-eslint` 8.71 + `eslint-plugin-react-hooks` 7.1.1 + `@eslint/js` 10. Nenhum serviço de domínio é alterado em nenhuma task. Máquina de 8 GB: rodar um processo pesado por vez.

---

## Phase 1: Setup

- [X] T001 Criar `<F>/package.json` (`"type": "module"`, scripts `dev`, `build` = `tsc --noEmit && vite build`, `test` = `vitest run`, `lint` = `eslint .`, `preview`) com as dependências e versões acima, `engines.node >= 22.12`; `<F>/tsconfig.json` (target ES2022, `jsx: react-jsx`, `strict`, `moduleResolution: bundler`, `types: ["vitest/globals", "@testing-library/jest-dom"]`, include `src` e `vite.config.ts`); `<F>/index.html`; `<F>/eslint.config.js` (flat: `@eslint/js` recommended + `typescript-eslint` recommended + regras de `eslint-plugin-react-hooks`, ignora `dist`); `<F>/.gitignore` (`node_modules`, `dist`) e `<F>/.dockerignore`. Rodar `npm install` e commitar `package-lock.json`. Registrar o resultado em `specs/019-lab-ui/research.md` seção "Validações empíricas (implementação)".
- [X] T002 Criar `<F>/vite.config.ts` (`defineConfig` de `vitest/config`, plugin react, `server.proxy`: `/api/orders`→`http://localhost:8081`, `/api/payments`→`:8082`, `/api/invoices`→`:8083`, `/health/order|payment|invoice`→ respectivos `/actuator/health` com `rewrite`; `test`: `environment: 'jsdom'`, `globals: true`, `setupFiles: ['./src/setupTests.ts']`) e `<F>/src/setupTests.ts` (`import '@testing-library/jest-dom/vitest'`).
- [X] T003 [P] Criar `<F>/public/config.js` (`window.__LAB_CONFIG__ = { kafbatUrl: "http://localhost:8090", kafbatCluster: "orderslab" };`) e `<F>/src/config.ts` (lê `window.__LAB_CONFIG__` com os mesmos padrões; exporta `kafbatTopicUrl(topic, tab?)` = `{kafbatUrl}/ui/clusters/{cluster}/all-topics/{topic}[/messages]`).

---

## Phase 2: Foundational (bloqueia todas as user stories)

- [X] T004 [P] Criar `<F>/src/domain/types.ts` conforme data-model.md: `Order`, `Payment`, `Invoice`, `OrderStatus`/`PaymentStatus`/`InvoiceStatus` (união de strings: `PENDING|CONFIRMED|CANCELLED`; `RESERVED|CONFIRMED|CANCELLED|FAILED`; `PENDING|ISSUED|CANCELLED|FAILED`), `ApiError` (`status`, `message`, `validationErrors?`, `path?`), `Health`, `StepState` (`done|failed|waiting|not_applicable|cancelled`) e `Flow`.
- [X] T005 [P] Criar `<F>/src/api/http.ts`: `request<T>(path, init?)` com `fetch` + `AbortSignal.timeout(3000)`; resposta não-2xx lança `ApiError` (lê o corpo `ErrorResponse` do serviço quando existir: `message`, `validationErrors`, `status`, `path`); falha de rede/timeout/corpo não-JSON lança `ApiError` com `status: 0` e mensagem "indisponível". Sem retry (o polling é o retry).
- [X] T006 [P] Criar `<F>/src/test/server.ts` (MSW 3: `setupServer()` de `msw/node`, helpers para handlers padrão) e o setup global em `<F>/src/setupTests.ts`: `beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))`, `afterEach(() => server.resetHandlers())`, `afterAll(() => server.close())`.
- [X] T007 [P] Criar `<F>/src/domain/scenarios.ts` (cinco cenários: `{id, label, amount, descricao}` — baixo 10.50, exato 500, entre 750, exato 1000, acima 1500 — com a descrição do resultado esperado) e `<F>/src/domain/topics.ts` (os 7 tópicos de evento e os 2 DLTs, com o serviço produtor e a ordem do fluxo).

**Checkpoint**: tipos, cliente HTTP, MSW e config prontos.

---

## Phase 3: User Story 1 — Criar um pedido e ver o fluxo acontecer (P1) 🎯 MVP

**Goal**: criar pedido (formulário e cenários), confirmar/cancelar e ver as três etapas chegarem ao estado final.

**Independent Test**: com os 3 serviços no ar e `npm run dev`, criar pelos cenários e ver os estados corretos (quickstart).

### Tests for User Story 1

- [X] T008 [P] [US1] `<F>/src/domain/flow.test.ts`: teste **por tabela** de `deriveFlow` cobrindo todas as combinações de status — pedido (`PENDING|CONFIRMED|CANCELLED`) × pagamento (ausente, `RESERVED`, `CONFIRMED`, `CANCELLED`, `FAILED`) × nota (ausente, `PENDING`, `ISSUED`, `CANCELLED`, `FAILED`): `feito`/`falhou`/`aguardando`/`não se aplica`/`cancelado`, `final`, várias linhas do mesmo `orderId` (vale a mais recente por `createdAt`) e os cinco cenários (baixo/500 → feito·feito·feito; 750/1000 → feito·feito·falhou; 1500 → feito·falhou·não se aplica).
- [X] T009 [P] [US1] `<F>/src/api/orders.test.ts` (MSW): `createOrder` → 201; 400 com `validationErrors` vira `ApiError` com a lista; 404/409 de `confirm`/`cancel` viram `ApiError` com `status`; timeout/503 → `status 0`/5xx "indisponível"; `getOrder`.
- [X] T010 [P] [US1] `<F>/src/components/NewOrderForm.test.tsx`: validação local (cliente vazio, valor vazio/0/negativo → mensagem no campo, sem chamar a API); envio válido chama `createOrder` e chama `onCreated(id)`; erro 400 do serviço aparece no formulário; clique em cada botão de cenário cria um pedido com o valor do cenário.

### Implementation for User Story 1

- [X] T011 [US1] Criar `<F>/src/domain/flow.ts`: `deriveFlow(order, payments, invoices): Flow` pura, seguindo a tabela da Decisão 4 (pedido ausente = `waiting`; filtra pagamentos/notas por `orderId` e usa o mais recente por `createdAt`; `final` = nenhuma etapa `waiting`).
- [X] T012 [P] [US1] Criar `<F>/src/api/orders.ts` (`createOrder({customerId, amount})`, `getOrder(id)`, `listOrders()`, `confirmOrder(id)`, `cancelOrder(id)`), `<F>/src/api/payments.ts` (`listPayments()`) e `<F>/src/api/invoices.ts` (`listInvoices()`) sobre `request` — rotas `/api/orders`, `/api/payments`, `/api/invoices`.
- [X] T013 [US1] Criar `<F>/src/components/NewOrderForm.tsx` (campos cliente e valor com validação local e erros por campo; envia via `createOrder`; exibe `validationErrors` `campo: mensagem` no campo correspondente e erros gerais; chama `onCreated(orderId)`) e `<F>/src/components/ScenarioButtons.tsx` (um botão por cenário de `scenarios.ts`, com a descrição do resultado esperado, que cria o pedido direto).
- [X] T014 [US1] Criar `<F>/src/hooks/useTrackedOrder.ts` (react-query: `getOrder(id)` + `listPayments()` + `listInvoices()` a cada 2 s enquanto o fluxo não é `final`; devolve `flow`, `order`, `payment`, `invoice` e o estado de cada painel) e `<F>/src/components/FlowTracker.tsx` (três etapas pedido → pagamento → nota com rótulo/ícone por `StepState`, valores e botões **Confirmar**/**Cancelar** do pedido enquanto `PENDING`, mensagem para 404/409).
- [X] T015 [US1] Criar `<F>/src/App.tsx` (seções: Novo pedido + cenários, Acompanhamento do pedido atual) e `<F>/src/main.tsx` (`QueryClientProvider` com `retry: false`, `staleTime: 0`), com `<F>/src/styles.css` básico e legível (sem dependência de CSS externa).
- [X] T016 [US1] Rodar `npm run lint && npm test && npm run build` em `<F>/` — T008–T010 verdes. Registrar em research.md.

**Checkpoint**: MVP funcional contra a stack real (validação empírica nos T036–T037).

---

## Phase 4: User Story 2 — Acompanhar o pedido sem ficar na dúvida (P2)

**Goal**: estados claros e polling que para (estado final ou ~30 s sem mudança), com link para o Kafbat nas falhas.

**Independent Test**: acompanhar um pedido normal (aguardando → feito) e um que não avança (serviço parado): para e mostra "aguardando".

### Tests for User Story 2

- [X] T017 [P] [US2] `<F>/src/hooks/useNoChangeTimeout.test.ts` (relógio falso do Vitest): o *fingerprint* muda → o contador reinicia; sem mudança por ~30 s → `stopped = true`; estado final → para imediatamente.
- [X] T018 [P] [US2] `<F>/src/components/FlowTracker.test.tsx` (MSW + relógio falso): pedido que evolui de aguardando para feito em ciclos de 2 s; pagamento `FAILED` → etapa de pagamento "falhou", nota "não se aplica", **link do Kafbat** para `payment.failed`/`order.created` visível; nota `FAILED` → link para `invoice.failed`; polling **para** ao ficar final e **para após ~30 s** sem mudança, exibindo "aguardando" sem indicador de carregamento; 404 ao acompanhar → "pedido não encontrado".

### Implementation for User Story 2

- [X] T019 [US2] Criar `<F>/src/hooks/useNoChangeTimeout.ts` (compara o *fingerprint* JSON dos status entre ciclos; expõe `stopped`; limite configurável, padrão 30 s) e integrá-lo em `useTrackedOrder.ts`: `refetchInterval` devolve `2000` enquanto `!final && !stopped` e `false` depois; ao parar por inatividade o estado vira `idle-waiting`.
- [X] T020 [US2] Atualizar `<F>/src/components/FlowTracker.tsx`: rótulos claros por estado (feito, falhou, aguardando, não se aplica, cancelado), mensagem "continua aguardando — atualização automática pausada" ao parar por inatividade (com botão **Atualizar agora**), link "Ver evento no Kafbat" nas etapas que falharam (usa `kafbatTopicUrl` do tópico correspondente), e mensagem de pedido não encontrado.
- [X] T021 [US2] Rodar `npm run lint && npm test && npm run build` em `<F>/` — T017–T018 verdes. Registrar em research.md.

**Checkpoint**: US1 + US2 funcionando.

---

## Phase 5: User Story 3 — Listas, saúde e tópicos (P3)

**Goal**: visão geral do laboratório com degradação por painel.

**Independent Test**: abrir listas e saúde; parar um serviço e ver só o painel dele indisponível; clicar num tópico e cair na página do Kafbat.

### Tests for User Story 3

- [X] T022 [P] [US3] `<F>/src/api/health.test.ts` (MSW): 200 `{status:"UP"}` → `UP`; **503 `{status:"DOWN"}`**, 502/504, timeout e rede → `DOWN`; corpo inesperado → `DOWN`.
- [X] T023 [P] [US3] `<F>/src/components/ServiceTable.test.tsx` (MSW): mostra no máximo os **200 mais recentes** (ordem por `createdAt` desc) e o aviso "lista limitada" quando houver mais; serviço fora do ar (503) mostra "indisponível" **mantendo o último dado** (segunda resposta falha após uma boa); lista vazia mostra mensagem (não em branco).
- [X] T024 [P] [US3] `<F>/src/components/TopicsCard.test.tsx` e `<F>/src/config.test.ts`: renderiza os **7 tópicos + 2 DLTs**, cada um com `href` `…/ui/clusters/orderslab/all-topics/<tópico>`; trocar `window.__LAB_CONFIG__` (URL base e cluster) muda os links sem recompilar; sem `config.js` usa os padrões.
- [X] T025 [P] [US3] `<F>/src/components/HealthPanel.test.tsx` (MSW): três serviços UP; um serviço DOWN marca só ele "fora do ar"; carregamento mostra "verificando".

### Implementation for User Story 3

- [X] T026 [P] [US3] Criar `<F>/src/api/health.ts` (`getHealth(svc: 'order'|'payment'|'invoice'): Promise<Health>` sobre `/health/{svc}`, sem lançar: qualquer falha ou `status !== 'UP'` vira `DOWN`).
- [X] T027 [US3] Criar `<F>/src/hooks/useServiceList.ts` (react-query com `refetchInterval: 2000` só enquanto montado e a aba visível, mantém o último dado em erro, ordena por `createdAt` desc e corta em 200 informando `truncated`) e `<F>/src/components/ServiceTable.tsx` (tabela genérica com colunas por serviço — pedidos: id, cliente, valor, status; pagamentos: id, pedido, valor, status; notas: id, pedido, pagamento, valor, status; aviso de truncamento; estado `unavailable`) e `<F>/src/components/Unavailable.tsx` (mensagem padrão "indisponível" com o último dado/horário).
- [X] T028 [US3] Criar `<F>/src/components/HealthPanel.tsx` (um cartão por serviço: "no ar"/"fora do ar"/"verificando", atualizando a cada 2 s enquanto visível) e `<F>/src/components/TopicsCard.tsx` (tópicos e DLTs agrupados por produtor/ordem do fluxo, cada item com link `target="_blank" rel="noreferrer"` para `kafbatTopicUrl(topic)` e outro para `.../messages`).
- [X] T029 [US3] Atualizar `<F>/src/App.tsx` com as seções/abas Listagens, Kafka e Saúde, garantindo que cada área trata o erro **independentemente** (uma falha não esconde as demais) e que nenhuma área fica em branco sem explicação.
- [X] T030 [US3] Rodar `npm run lint && npm test && npm run build` em `<F>/` — T022–T025 verdes. Registrar em research.md.

**Checkpoint**: todas as telas entregues.

---

## Phase 6: Implantação e CI (Princípios I, IV)

- [X] T031 [P] Criar `<F>/nginx/default.conf.template` exatamente como validado na sonda (research.md, Decisão 2): `log_format json escape=json`, `access_log /dev/stdout json`, `resolver ${NGINX_LOCAL_RESOLVERS} valid=10s ipv6=off`, `location /api/orders|payments|invoices` com `set $u ${..._API_URL}; proxy_pass $u;` e timeouts de 3 s, `location = /health/order|payment|invoice` com `rewrite ^ /actuator/health break;`, `location = /config.js` sem cache e fallback `try_files $uri /index.html`; e `<F>/nginx/40-config-js.sh` (gera `/usr/share/nginx/html/config.js` com `kafbatUrl` e `kafbatCluster` a partir de `KAFBAT_URL`/`KAFBAT_CLUSTER`, padrões `http://localhost:8090`/`orderslab`).
- [X] T032 Criar `<F>/Dockerfile` multi-stage: estágio `node:22-alpine` (`npm ci`, `npm run build`) → `nginx:alpine` (copia `dist/` para `/usr/share/nginx/html`, o template para `/etc/nginx/templates/default.conf.template` e o script para `/docker-entrypoint.d/40-config-js.sh` com `chmod +x`; `ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1`; `EXPOSE 80`). Sem remover o `config.js` padrão de `public/`, que o script sobrescreve.
- [X] T033 [P] Editar `infra/docker-compose.yml` adicionando o serviço `frontend` (`build: ../frontend-react`, `container_name: frontend-react`, `ports: "3000:80"`, `environment`: `ORDER_API_URL=http://order-service:8080`, `PAYMENT_API_URL=http://payment-service:8080`, `INVOICE_API_URL=http://invoice-service:8080`, `KAFBAT_URL=http://localhost:8090`, `KAFBAT_CLUSTER=orderslab`; `depends_on` os três serviços; `networks: lab-network`) e criar `infra/k8s/frontend-deployment.yaml` (namespace `orderslab`, `image: frontend-react`, `imagePullPolicy: Never`, porta 80, env com `http://order-service:8081`, `http://payment-service:8082`, `http://invoice-service:8083`) e `infra/k8s/frontend-service.yaml` (`LoadBalancer`, porta `3000` → `targetPort 80`), no padrão dos manifests existentes.
- [X] T034 [P] Criar `.github/workflows/frontend-ci.yml` (`workflow_call` com input `project-name`; jobs `build-and-test` (`actions/setup-node@v4` Node 22 com cache npm, `npm ci`, `npm run lint`, `npm test`, `npm run build`, upload do `dist`), `security` (CodeQL `languages: javascript-typescript`, `build-mode: none`, categoria `/language:javascript-typescript-<project>`) e `package` (`needs: [build-and-test, security]`, `if: startsWith(github.ref, 'refs/tags/')`, extrai a versão da tag `frontend-react-vX.Y.Z` e faz `docker build`)) e `.github/workflows/frontend-react.yml` (gatilho fino espelhando `order-api.yml`: push em qualquer branch e tags `frontend-react-v*.*.*`, PR com `paths` `frontend-react/**` e os dois workflows, `permissions: contents read / security-events write`, `uses: ./.github/workflows/frontend-ci.yml`).

---

## Phase 7: Validação empírica (stack real)

- [X] T035 Validar a **imagem nginx real** (quickstart "Imagem nginx"): `docker build -t frontend-react frontend-react`; subir com env de Compose apontando `host.docker.internal:8081-8083`; conferir `config.js` com a URL, `/health/order` → `UP`, `/api/orders` → lista, upstream inexistente → 502 **sem derrubar** o contêiner, log de acesso JSON; também `docker compose -f infra/docker-compose.yml config` (e `kubectl apply --dry-run=client -f infra/k8s/frontend-*.yaml` se `kubectl` existir). Registrar em research.md.
- [X] T036 Subir a stack real **sem rodar tudo em contêiner** (8 GB): `postgres-api`, `kafka`, `kafbat-ui` do Compose e os 3 serviços por `spring-boot:run` (portas 8081–8083), mais `npm run dev` em `<F>/`; percorrer os **5 cenários** (e confirmar/cancelar) e conferir os estados finais do quickstart, em ≤ 15 s (SC-001/SC-002); validar as listagens (200 mais recentes), o formulário (400 de validação) e o 404 de pedido inexistente. Registrar em research.md.
- [X] T037 Validar a degradação e os links: parar um serviço de cada vez → só o painel dele "indisponível" e o polling para; parar o Postgres → saúde "fora do ar"; conferir que o polling para em estado final e após ~30 s; abrir os **9 links** do cartão de tópicos contra o Kafbat real (sem hífens a mais, cluster `orderslab`) e a troca de `KAFBAT_URL` sem recompilar. Registrar em research.md. Ao terminar, **parar** serviços e contêineres usados.

---

## Phase 8: Polish & Cross-Cutting

- [X] T038 Rodar a suíte completa do front (`npm run lint && npm test && npm run build`) medindo o tempo (SC-007: < 2 min) e confirmar `git diff --stat`: **nada** em `order-api/`, `payment-api/` ou `invoice-api/` (FR-012, SC-006).
- [X] T039 [P] Atualizar `README.md` (seção do frontend: como rodar, portas, variáveis, cenários; remover o "(ainda não criado)" do `frontend-react/`) e `ROADMAP.md`: marcar **U1** como ✅ concluído (data, o que foi entregue, achados da sonda — TypeScript preso em 6.0, mudança de API do MSW 3, DNS tardio no nginx — e as validações) e ajustar a nota do **E5.5** (agora existe um frontend no repo).
- [X] T040 [P] Marcar todas as tasks acima como `[X]`.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 (bloqueia tudo) → US1 → US2 → US3 → Implantação/CI → Validação empírica → Polish.
- US2 depende de US1 (tracker e hook); US3 é independente de US2, mas reaproveita `http.ts`, o MSW e `config.ts`.
- Dentro de cada story: testes (marcados [P]) antes da implementação; T011 (`deriveFlow`) antes de T014; T012 antes de T013/T014.
- Paralelo: T003–T007 (arquivos distintos); testes de cada story; T031, T033, T034 (arquivos distintos). T035–T037 são sequenciais (usam a máquina de 8 GB).

## Implementation Strategy

- **MVP**: Phases 1–3 (US1) — criar pedido e ver o fluxo; já validável contra a stack real.
- Incremental: US2 torna o acompanhamento claro e finito; US3 completa a visão geral; Implantação/CI fecha a governança; a validação empírica confirma tudo contra os serviços reais.
- Parar e validar a cada checkpoint (`npm run lint && npm test && npm run build`).
