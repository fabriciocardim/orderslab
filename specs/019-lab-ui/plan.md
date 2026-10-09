# Implementation Plan: UI do Laboratório

**Branch**: `019-lab-ui` | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-lab-ui/spec.md`

## Summary

Projeto novo `frontend-react/`: SPA React + Vite + TypeScript servida por nginx na porta `3000`, que **faz proxy por
prefixo** para `order-api`, `payment-api` e `invoice-api` (`/api/orders|payments|invoices`) e para o
`/actuator/health` de cada um (`/health/*`) — mesma origem, sem CORS e **sem alterar nenhum serviço**. Telas: novo pedido
com cenários de valor e confirmar/cancelar; acompanhamento por pedido (pedido → pagamento → nota) derivado dos status
por uma função pura; listagens (200 mais recentes); saúde; cartão de tópicos/DLTs com *deep links* para o Kafbat.
Polling de 2 s com parada por estado final ou ~30 s sem mudança, timeout de 3 s e degradação por painel. Decisões e
evidências (sondas de toolchain, nginx e Kafbat) em [research.md](./research.md).

## Technical Context

**Language/Version**: TypeScript ~6.0.3, React 19.3, Node ≥ 22.12 (Node 26.10 local)

**Primary Dependencies**: `react`/`react-dom` 19.3.0, `@tanstack/react-query` 5.104.1; build: Vite 8.3.2 +
`@vitejs/plugin-react` 6.1.2; testes: Vitest 5.0.3 + jsdom 30.1.2 + Testing Library + MSW 3.0.2; lint: ESLint 10.12 +
`typescript-eslint` 8.71 + `eslint-plugin-react-hooks` 7.1.1

**Storage**: nenhum (só lê as APIs; sem estado persistido)

**Testing**: Vitest (derivação por tabela, cliente de API com MSW, componentes), ESLint, `tsc --noEmit`, `vite build`;
validação empírica contra a stack real

**Target Platform**: navegador moderno; contêiner `nginx:alpine` (Compose e k8s local)

**Project Type**: aplicação web (frontend) — projeto novo e independente

**Performance Goals**: etapas chegam ao estado final correto em ≤ 15 s após criar o pedido (SC-001); suíte < 2 min (SC-007)

**Constraints**: nenhum serviço de domínio alterado (FR-012/FR-017, SC-006); só REST (Princípio I); sem backend, sem
Kafka/banco direto; sem autenticação e sem OTel de navegador na v1

**Scale/Scope**: 1 SPA, ~12 componentes, 1 função de domínio central, 4 clientes de API, 1 Dockerfile, 2 arquivos de
nginx, 2 workflows, 1 serviço no Compose, 1 Deployment + 1 Service no k8s

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — projeto próprio (`package.json`, Dockerfile, pipeline); fala com os serviços **só por REST**; nenhum código/dependência compartilhado; serviços intocados. |
| II. Funcionalidade técnica real | **OK** — exercita o fluxo real contra os serviços reais (nada simulado na tela); validação empírica dos 5 cenários. |
| III. Persistência real | **N/A** — a UI não guarda estado. Também não lê bancos (só APIs). |
| IV. Portabilidade | **OK** — imagem `nginx:alpine` padrão, sem recurso proprietário; Compose + k8s no padrão dos demais; destinos do proxy por variável (porta do Service no k8s ≠ porta interna no Compose). |
| V. Segurança | **N/A hoje** — sem autenticação (Fase 5). Registrado: esta SPA é o frontend a integrar no E5.5. |
| VI. Observabilidade | **OK com decisão explícita** — log de acesso do nginx em JSON; **sem OpenTelemetry de navegador na v1** (declarado na spec e aqui); tracing distribuído é da Fase 6. |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **DECLARADO** — a UI não estava no ROADMAP; foi adicionada como item **U1** (complemento após a Fase 2) em `ROADMAP.md` antes desta spec. |
| Padrões de qualidade/CI | **OK com adaptação** — `service-ci.yml` é de Maven; o front ganha `frontend-ci.yml` reutilizável + gatilho fino `frontend-react.yml` (lint, testes, build, CodeQL JS/TS, empacotamento só após os gates, tag `frontend-react-vX.Y.Z`). |

**Pós-design**: reavaliado após `data-model.md`, `contracts/` e `quickstart.md` — sem violações; a única adaptação
(CI próprio do front) está justificada e não é desvio. Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/019-lab-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── services-consumed.md     # REST/health dos 3 serviços como a UI os consome
│   └── proxy-and-config.md      # rotas do nginx, variáveis de ambiente e config.js
├── checklists/requirements.md
└── tasks.md                     # gerado por /speckit-tasks
```

### Source Code (repository root)

```text
frontend-react/
├── package.json  package-lock.json  tsconfig.json  vite.config.ts  eslint.config.js  index.html  .dockerignore
├── Dockerfile                                   # node:22-alpine (build) → nginx:alpine
├── nginx/
│   ├── default.conf.template                    # proxies por prefixo, resolver tardio, log JSON, fallback SPA
│   └── 40-config-js.sh                          # gera config.js (kafbatUrl, kafbatCluster) no start
├── public/config.js                             # padrão de dev (sobrescrito no contêiner)
└── src/
    ├── main.tsx  App.tsx  config.ts  styles.css  setupTests.ts
    ├── api/        http.ts (fetch + timeout 3 s)  orders.ts  payments.ts  invoices.ts  health.ts
    ├── domain/     types.ts  flow.ts (deriveFlow)  scenarios.ts  topics.ts  flow.test.ts
    ├── hooks/      useTrackedOrder.ts  useNoChangeTimeout.ts  useServiceList.ts
    ├── components/ NewOrderForm  ScenarioButtons  FlowTracker  ServiceTable  HealthPanel  TopicsCard  Unavailable
    └── (testes ao lado do código: *.test.ts / *.test.tsx; MSW em src/test/server.ts)
.github/workflows/frontend-ci.yml  frontend-react.yml
infra/docker-compose.yml                         # + serviço frontend
infra/k8s/frontend-deployment.yaml  frontend-service.yaml
README.md  ROADMAP.md                            # atualizados ao concluir
```

**Structure Decision**: um único projeto `frontend-react/` no padrão previsto no README; sem tocar em `order-api`,
`payment-api` nem `invoice-api`. Fora do projeto, apenas os arquivos de implantação/CI e a documentação.

## Complexity Tracking

Sem violações da constitution a justificar.
