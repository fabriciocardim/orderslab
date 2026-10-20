# Implementation Plan: Filtro por Pedido no Polling e Listas Limitadas

**Branch**: `021-filtro-polling-por-pedido` (na branch git `feature/T001_teste_carga`) | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-filtro-polling-por-pedido/spec.md`

## Summary

A UI baixa a cada 2 s as listas completas de pagamentos e notas para achar um pedido, e isso derrubou a produção no
teste de carga T1. A correção: `GET /api/payments?orderId=` e `GET /api/invoices?orderId=` devolvem só os registros do
pedido; as listagens sem filtro (inclusive `/api/orders`) passam a devolver os N mais recentes (`?limit=`, padrão 200,
1–500) com o total em `X-Total-Count`; Flyway V3 cria os índices; a UI (`useTrackedOrder`, `useServiceList`) e o k6
passam a usar as rotas novas. Ver [research.md](./research.md) e [contracts/list-endpoints.md](./contracts/list-endpoints.md).

## Technical Context

**Language/Version**: Java 21 (Spring Boot 4.1.1) nos 3 serviços; TypeScript 6.0 + React 19 (Vite 8) no frontend; JS (k6 v2) na suíte de carga

**Primary Dependencies**: Spring Web MVC, Spring Data JPA, Bean Validation (já presentes); TanStack Query, MSW 3, Vitest (já presentes) — nenhuma dependência nova

**Storage**: Postgres 15 (um banco por serviço); migração Flyway `V3__*` em cada serviço

**Testing**: `@WebMvcTest` (controller), Mockito (service), `@SpringBootTest` + Testcontainers Postgres (persistência real); Vitest + Testing Library + MSW (frontend); `k6 inspect` + smoke contra mock (k6)

**Target Platform**: containers Linux (Compose e k8s), sem mudança de infra

**Project Type**: microsserviços web + SPA

**Performance Goals**: atualização do acompanhamento < 2 KB por rodada; consulta por pedido p95 < 100 ms com 10 mil linhas (SC-001, SC-003)

**Constraints**: formato dos registros inalterado (FR-007); resposta continua array; erro no formato `ErrorResponse`

**Scale/Scope**: 3 serviços (2 com filtro, 3 com limite), 1 SPA, 1 script k6

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Independência dos Serviços | **OK** — cada serviço muda só o próprio controller/service/repository/migração; nada compartilhado; a UI fala com cada serviço pelo contrato REST dele. |
| II. Funcionalidade técnica real | **OK** — consultas reais ao banco, sem cache artificial; `orderId` e `limit` validados antes de processar (400 no formato padrão). |
| III. Persistência real | **OK** — índices via migração Flyway versionada em cada banco; `ddl-auto=validate` continua. |
| IV. Portabilidade | **OK** — nenhuma variável, porta ou manifest novo; proxy nginx inalterado. |
| V. Segurança | **N/A**. |
| VI. Observabilidade | **OK** — as rotas continuam instrumentadas pelo starter OTel (`http.server.requests` por template de URI, traces); log estruturado (`debug`) com `orderId`/`limit`/quantidade em cada consulta, no padrão `log.atX().addKeyValue(...)`. |
| VII. Remediação autônoma | **N/A**. |
| Governance — adiantamento | **Declarado** — item T2 (complemento "Desempenho sob carga"), fora da ordem das fases; motivado pela evidência do T1. |

**Pós-design**: reavaliado após `research.md` e `contracts/` — sem violações; Complexity Tracking vazio.

## Project Structure

### Documentation (this feature)

```text
specs/021-filtro-polling-por-pedido/
├── spec.md
├── plan.md              # este arquivo
├── research.md          # decisões + validações empíricas
├── contracts/
│   └── list-endpoints.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
payment-api/ (idem invoice-api/, trocando Payment→Invoice)
├── src/main/java/com/orderslab/payment_api/
│   ├── controller/PaymentController.java       # GET com orderId/limit, X-Total-Count
│   ├── dto/PaymentPage.java                    # novo: record(items, total)
│   ├── exception/GlobalExceptionHandler.java   # + type mismatch e method validation → 400
│   ├── repository/PaymentRepository.java       # + findByOrderIdOrderByCreatedAtDesc
│   └── service/PaymentService.java             # findAll → findByOrderId + findRecent
├── src/main/resources/db/migration/V3__index_payments_order_id_created_at.sql
└── src/test/java/com/orderslab/payment_api/
    ├── controller/PaymentControllerTest.java
    ├── service/PaymentServiceTest.java
    └── PaymentApiApplicationTests.java         # filtro/limite/índices com Postgres real

order-api/   # só limite: controller, dto/OrderPage, exception handler, service, V3 (created_at), testes

frontend-react/src/
├── api/http.ts              # + requestList → { items, total }
├── api/payments.ts          # + listPaymentsByOrder; listPayments(limit) → { items, total }
├── api/invoices.ts          # idem
├── api/orders.ts            # listOrders(limit) → { items, total }
├── hooks/useTrackedOrder.ts # usa as rotas por pedido
├── hooks/useServiceList.ts  # fetcher → { items, total }; truncated pelo total
├── components/ServiceTable.tsx
└── App.tsx                  # fetchers das listagens

load-tests/blackfriday.js    # track() e visitante() nas rotas novas
```

**Structure Decision**: mudança simétrica em payment-api e invoice-api (cópias próprias, Princípio I); order-api recebe
só o limite; frontend e k6 consomem o contrato novo.

## Complexity Tracking

Sem violações.
