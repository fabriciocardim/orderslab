# Implementation Plan: Máquina de Estados / Guardas de Transição

**Branch**: `005-state-transition-guards` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-state-transition-guards/spec.md`

## Summary

Adicionar guardas de transição de estado nos 3 serviços: `confirm()`/`cancel()`/`issue()`
só têm sucesso a partir do estado inicial acionável de cada recurso; caso contrário, uma
nova exceção `InvalidStatusTransitionException` (por serviço, independente) é mapeada para
`409 Conflict` pelo `GlobalExceptionHandler` já existente. Junto, remover os 4 valores de
enum sem uso real (`OrderStatus.COMPLETED`, `PaymentStatus.PENDING`, `PaymentStatus.FAILED`,
`InvoiceStatus.FAILED`) — decisão já tomada, ver [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: nenhuma nova — reutiliza Spring MVC já presente

**Storage**: N/A (guarda opera sobre o estado em memória já existente)

**Testing**: validação empírica via boot + `curl` (mesmo padrão dos itens 1.3/1.4)

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: 3 serviços backend Maven independentes — edição de `Service`/`*Status`
enum + 1 exceção nova + 1 handler novo por serviço

**Performance Goals**: N/A

**Constraints**: nenhuma mudança de comportamento fora do escopo das transições; nenhuma
lib de máquina de estados compartilhada (Princípio I); controle de concorrência real fica
fora de escopo (ver Assumptions da spec)

**Scale/Scope**: 3 serviços × (1 enum editado + 1 Service editado + 1 exceção nova + 1
handler editado) = 12 arquivos tocados; `payment-api` ganha edição extra no `Payment.java`
(construtor)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | `InvalidStatusTransitionException` implementada 3x, independente, sem lib compartilhada. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Fecha um gap central do Princípio II — hoje o sistema aceita transições logicamente impossíveis; a guarda é exatamente "fluxo técnico real". **PASS** |
| III–VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `data-model.md`/`contracts/error-409.md` confirmam reuso
do `ErrorResponse` já existente, sem novo contrato paralelo — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/005-state-transition-guards/
├── plan.md                    # This file
├── research.md                # Phase 0 output
├── data-model.md              # Phase 1 output
├── contracts/error-409.md     # Phase 1 output
├── spec.md
└── tasks.md                   # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
order-api/.../model/OrderStatus.java                          # remove COMPLETED
order-api/.../service/OrderService.java                        # + guarda em confirm/cancel
order-api/.../exception/InvalidStatusTransitionException.java  # novo
order-api/.../exception/GlobalExceptionHandler.java             # + handler 409

payment-api/.../model/PaymentStatus.java                       # remove PENDING, FAILED
payment-api/.../model/Payment.java                              # construtor cria como RESERVED
payment-api/.../service/PaymentService.java                     # + guarda; remove setStatus redundante
payment-api/.../exception/InvalidStatusTransitionException.java # novo
payment-api/.../exception/GlobalExceptionHandler.java            # + handler 409

invoice-api/.../ (mesmo padrão de order-api, sem FAILED)
```

**Structure Decision**: edição in-place + 1 arquivo novo por serviço
(`InvalidStatusTransitionException.java`), mesmo pacote `exception` onde já moram
`*NotFoundException`/`ErrorResponse`/`GlobalExceptionHandler`.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
