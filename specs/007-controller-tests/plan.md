# Implementation Plan: Testes de Controller (@WebMvcTest)

**Branch**: `007-controller-tests` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-controller-tests/spec.md`

## Summary

Escrever, para cada um dos 3 controllers, uma classe `<Entity>ControllerTest` (`@WebMvcTest`
+ `@MockitoBean` para o `Service`, `MockMvc` para exercitar requisições) cobrindo os 9 casos
descritos em [research.md](./research.md): criação válida/inválida, busca existente/
inexistente, listagem, e as 2 ações de transição (sucesso/conflito). Pacotes exatos das
anotações (Spring Boot 4.1.1) já confirmados via inspeção de jar. Nenhuma mudança em código
de produção.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: `spring-boot-starter-webmvc-test` (já presente, item 1.1) —
`@WebMvcTest`/`@MockitoBean`/`MockMvc` já disponíveis; nenhuma dependência nova

**Storage**: N/A (Service mockado, não toca o armazenamento real)

**Testing**: esta feature É a suíte de testes de Controller — sem meta-testes

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: 3 classes de teste novas, uma por serviço, sem alteração de código de
produção

**Performance Goals**: N/A

**Constraints**: nenhuma mudança de comportamento de produção; `Service` sempre mockado
(não a implementação real); implementação independente por serviço (Princípio I)

**Scale/Scope**: 3 arquivos novos (`OrderControllerTest.java`, `PaymentControllerTest.java`,
`InvoiceControllerTest.java`), 9 casos de teste cada (27 no total)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | 3 classes de teste independentes, sem classe base compartilhada. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Testes automatizados de contrato HTTP são "fluxo técnico real" — substitui validação manual por `curl` (itens 1.3-1.5) por verificação repetível. **PASS** |
| III–VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `research.md` confirma que nenhuma dependência nova é
necessária, pacotes das anotações já verificados, e nenhum código de produção muda — segue
**PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/007-controller-tests/
├── plan.md              # This file
├── research.md          # Phase 0 output — pacotes confirmados + matriz de casos
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md`, `contracts/` nem `quickstart.md` novos — não há entidades/interfaces
novas, e o contrato HTTP já está documentado em
`specs/003-http-404-error-handling/contracts/error-404.md`,
`specs/004-request-validation/contracts/error-400.md` e
`specs/005-state-transition-guards/contracts/error-409.md`.

### Source Code (repository root)

```text
order-api/src/test/java/com/orderslab/order_api/controller/OrderControllerTest.java     # novo
payment-api/src/test/java/com/orderslab/payment_api/controller/PaymentControllerTest.java # novo
invoice-api/src/test/java/com/orderslab/invoice_api/controller/InvoiceControllerTest.java # novo
```

**Structure Decision**: novo pacote `controller` sob `src/test/java` em cada serviço
(paralelo ao pacote `service` já criado no item 1.6).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
