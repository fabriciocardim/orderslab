# Implementation Plan: Tratamento de Erro HTTP — 404 Real

**Branch**: `003-http-404-error-handling` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-http-404-error-handling/spec.md`

## Summary

Adicionar, de forma independente em cada um dos 3 serviços, um `@RestControllerAdvice` que
mapeia a respectiva exceção `*NotFoundException` para HTTP 404, com um corpo de erro
estruturado (`ErrorResponse`: timestamp, status, error, message, path) idêntico em formato
entre os 3 serviços. Ver [research.md](./research.md) para as decisões técnicas e
[data-model.md](./data-model.md)/[contracts/error-404.md](./contracts/error-404.md) para o
formato exato.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring MVC (`spring-boot-starter-webmvc`, já presente); nenhuma
dependência nova

**Storage**: N/A

**Testing**: `@WebMvcTest` por serviço, exercitando o handler novo (ver Fase 3 em tasks.md)

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: 3 serviços backend Maven independentes — 2 classes novas por serviço
(`ErrorResponse`, `GlobalExceptionHandler`)

**Performance Goals**: N/A

**Constraints**: Nenhuma mudança de comportamento em endpoints de sucesso; nenhuma
biblioteca compartilhada entre os 3 serviços (Princípio I); handler específico à exceção
`*NotFoundException`, não genérico a `RuntimeException`

**Scale/Scope**: 3 serviços × 2 arquivos novos cada (`ErrorResponse.java`,
`GlobalExceptionHandler.java`) = 6 arquivos novos; 0 arquivos existentes modificados
(controllers e `*NotFoundException` permanecem como estão)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | `ErrorResponse`/`GlobalExceptionHandler` duplicados de forma independente em cada serviço, sem módulo/lib compartilhada. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Corrige um fluxo técnico incompleto (404 real em vez de 500 genérico) — exatamente o tipo de gap que o Princípio II não permite deixar para depois. **PASS** |
| VI. Observabilidade | Sim (leve) | Nenhuma instrumentação OTel formal nesta feature (isso é item separado do ROADMAP); não há conflito, já que o handler não impede instrumentação futura. **PASS**, sem ação adicional aqui. |
| III, IV, V, VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `data-model.md` e `contracts/error-404.md` confirmam um
único DTO simples (`ErrorResponse`) sem relacionamento com outras entidades, e um contrato
de erro idêntico nos 3 serviços — segue **PASS**, sem novas violações introduzidas pelo
design.

## Project Structure

### Documentation (this feature)

```text
specs/003-http-404-error-handling/
├── plan.md                    # This file
├── research.md                # Phase 0 output
├── data-model.md              # Phase 1 output — formato do ErrorResponse
├── contracts/error-404.md     # Phase 1 output — contrato da resposta 404
├── quickstart.md              # Como validar manualmente
├── spec.md
└── tasks.md                   # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
order-api/src/main/java/com/orderslab/order_api/exception/
├── OrderNotFoundException.java   # existente, sem alteração
├── ErrorResponse.java            # novo
└── GlobalExceptionHandler.java   # novo

payment-api/src/main/java/com/orderslab/payment_api/exception/
├── PaymentNotFoundException.java # existente, sem alteração
├── ErrorResponse.java            # novo
└── GlobalExceptionHandler.java   # novo

invoice-api/src/main/java/com/orderslab/invoice_api/exception/
├── InvoiceNotFoundException.java # existente, sem alteração
├── ErrorResponse.java            # novo
└── GlobalExceptionHandler.java   # novo
```

**Structure Decision**: novo conteúdo dentro do pacote `exception` já existente em cada
serviço (onde já mora a respectiva `*NotFoundException`), mantendo a mesma convenção de
pacotes já usada no projeto. Nenhum controller é alterado.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
