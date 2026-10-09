# Implementation Plan: Testes Unitários de Service

**Branch**: `006-service-unit-tests` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-service-unit-tests/spec.md`

## Summary

Escrever, para cada um dos 3 serviços, uma classe `<Entity>ServiceTest` (JUnit 5 + AssertJ,
sem contexto Spring) cobrindo criação, busca, 404, transição válida e transição bloqueada —
ver [research.md](./research.md) para a matriz completa de casos. Nenhuma mudança em código
de produção; feature é puramente adição de testes.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: JUnit 5 (Jupiter) + AssertJ, já disponíveis via
`spring-boot-starter-*-test` (itens 1.1-1.3); nenhuma dependência nova

**Storage**: N/A

**Testing**: esta feature É a suíte de testes — sem meta-testes

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: 3 classes de teste novas, uma por serviço, sem alteração de código de
produção

**Performance Goals**: N/A

**Constraints**: nenhuma mudança de comportamento de produção; testes independentes entre si
(sem estado compartilhado entre casos); implementação independente por serviço (Princípio I)

**Scale/Scope**: 3 arquivos novos (`OrderServiceTest.java`, `PaymentServiceTest.java`,
`InvoiceServiceTest.java`), ~7-9 casos de teste cada

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | 3 classes de teste independentes, sem classe base compartilhada. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Testes automatizados são exatamente o tipo de "fluxo técnico real" que o Princípio II cobra — substitui validação manual por `curl` por verificação repetível. **PASS** |
| III–VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `research.md` confirma que nenhuma dependência nova é
necessária e nenhum código de produção muda — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/006-service-unit-tests/
├── plan.md              # This file
├── research.md          # Phase 0 output — matriz de casos de teste
├── quickstart.md         # Como rodar os testes
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md` nem `contracts/` — feature não introduz entidades nem interfaces novas.

### Source Code (repository root)

```text
order-api/src/test/java/com/orderslab/order_api/service/OrderServiceTest.java     # novo
payment-api/src/test/java/com/orderslab/payment_api/service/PaymentServiceTest.java # novo
invoice-api/src/test/java/com/orderslab/invoice_api/service/InvoiceServiceTest.java # novo
```

Nenhum arquivo em `src/main/` é alterado.

**Structure Decision**: novo pacote `service` sob `src/test/java` em cada serviço (não
existe ainda — hoje só há o `*ApplicationTests.java` direto no pacote raiz de teste).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
