# Implementation Plan: Validação de Request (Bean Validation)

**Branch**: `004-request-validation` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-request-validation/spec.md`

## Summary

Adicionar Bean Validation aos 3 serviços: `spring-boot-starter-validation` nos `pom.xml`,
anotações `@NotBlank`/`@NotNull`/`@Positive`/`@Pattern` nos 3 DTOs de request, `@Valid` nos
3 controllers, e um novo `@ExceptionHandler(MethodArgumentNotValidException.class)` em cada
`GlobalExceptionHandler` já existente (item 1.3), retornando 400 com o `ErrorResponse`
estendido (novo campo `validationErrors`). Ver [research.md](./research.md) para as decisões
técnicas e [data-model.md](./data-model.md)/[contracts/error-400.md](./contracts/error-400.md)
para o formato exato.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: `spring-boot-starter-validation:4.1.1` (nova, confirmada
resolvível — ver research.md); reutiliza `spring-boot-starter-webmvc` já presente

**Storage**: N/A

**Testing**: Validação empírica via boot + `curl` (mesmo padrão do item 1.3); testes
automatizados formais ficam para os itens 1.6/1.7 do ROADMAP

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: 3 serviços backend Maven independentes — edição de DTOs existentes +
extensão de classes criadas no item 1.3

**Performance Goals**: N/A

**Constraints**: Nenhuma mudança de comportamento em requisições válidas; nenhuma biblioteca
de validação compartilhada entre os 3 serviços (Princípio I); `orderId`/`paymentId`
permanecem `String` (não migram para `UUID`) — decisão já registrada na spec

**Scale/Scope**: 3 serviços × (1 `pom.xml` + 1 DTO + 1 controller + `ErrorResponse.java` +
`GlobalExceptionHandler.java` editados) = 15 edições em arquivos já existentes; 0 arquivos
novos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Validação implementada de forma independente nos 3 serviços, anotações Jakarta padrão, sem lib de validação compartilhada. **PASS** |
| II. Funcionalidade Técnica Real | Sim | Fecha um gap explícito do Princípio II (validação de entrada como parte de "funcionalidade técnica real") — exatamente o tipo de trabalho que este princípio exige. **PASS** |
| III–VII | Não aplicável | Feature não toca persistência, cloud/portabilidade, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `data-model.md`/`contracts/error-400.md` confirmam que a
extensão do `ErrorResponse` é compatível com o contrato já existente (item 1.3), sem quebrar
o formato usado pelo 404 — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/004-request-validation/
├── plan.md                    # This file
├── research.md                # Phase 0 output
├── data-model.md              # Phase 1 output
├── contracts/error-400.md     # Phase 1 output
├── spec.md
└── tasks.md                   # Phase 2 output (/speckit-tasks command)
```

Sem `quickstart.md` novo — a validação será feita com o mesmo procedimento de boot+`curl` já
documentado em `specs/003-http-404-error-handling/quickstart.md`, só trocando o corpo da
requisição; não há necessidade de duplicar o guia.

### Source Code (repository root)

```text
order-api/.../pom.xml                                  # + spring-boot-starter-validation
order-api/.../dto/OrderRequest.java                     # + anotações
order-api/.../controller/OrderController.java            # + @Valid
order-api/.../exception/ErrorResponse.java                # + validationErrors, + construtor
order-api/.../exception/GlobalExceptionHandler.java        # + handler de MethodArgumentNotValidException

payment-api/.../ (mesmo padrão, PaymentReservationRequest/PaymentController)
invoice-api/.../ (mesmo padrão, InvoiceRequest/InvoiceController)
```

**Structure Decision**: edição in-place dos arquivos já existentes (nenhum arquivo/pacote
novo) — feature de extensão, não de criação de estrutura nova.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
