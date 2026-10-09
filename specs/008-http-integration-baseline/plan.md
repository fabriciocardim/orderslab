# Implementation Plan: Teste de Integração HTTP Baseline

**Branch**: `008-http-integration-baseline` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-http-integration-baseline/spec.md`

## Summary

Reescrever, em cada um dos 3 serviços, o `*ApplicationTests.java` existente (hoje um
`contextLoads()` vazio) para um teste `@SpringBootTest(webEnvironment = RANDOM_PORT)` +
`@AutoConfigureRestTestClient` que exercita o fluxo real via HTTP: criar → transicionar →
buscar (confirmando persistência entre requisições) → uma segunda transição num segundo
recurso. Ver [research.md](./research.md) para a decisão de usar `RestTestClient` (Spring
Framework 7) em vez do `TestRestTemplate` clássico.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: `RestTestClient`/`@AutoConfigureRestTestClient`, já disponíveis
via `spring-boot-starter-webmvc-test` (item 1.1); nenhuma dependência nova

**Storage**: N/A (armazenamento em memória atual, sem mudança)

**Testing**: esta feature É o teste de integração — sem meta-testes

**Target Platform**: JVM (mesmo ambiente de build atual)

**Project Type**: reescrita de 3 arquivos de teste já existentes, sem alteração de código
de produção

**Performance Goals**: N/A

**Constraints**: nenhuma mudança de comportamento de produção; teste roda com servidor real
(porta aleatória), sem mocks; implementação independente por serviço (Princípio I)

**Scale/Scope**: 3 arquivos reescritos (`*ApplicationTests.java` em cada serviço)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | 3 testes independentes, sem classe base compartilhada. **PASS** |
| II. Funcionalidade Técnica Real | Sim | É exatamente o tipo de verificação "ponta a ponta real" que o Princípio II pede — substitui um placeholder vazio por um teste de fluxo completo, sem mocks. **PASS** |
| III–VII | Não aplicável | Feature não toca persistência real, cloud/portabilidade, IAM, observabilidade nem agentes de SRE — continua em memória, dentro do já previsto pelo Princípio III para o estágio atual. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `research.md` confirma que nenhuma dependência nova é
necessária e nenhum código de produção muda — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/008-http-integration-baseline/
├── plan.md              # This file
├── research.md          # Phase 0 output — decisão RestTestClient + fluxo coberto
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md`/`contracts/`/`quickstart.md` novos — não há entidades/interfaces novas,
e os contratos HTTP já estão documentados nos itens 1.3-1.5.

### Source Code (repository root)

```text
order-api/src/test/java/com/orderslab/order_api/OrderApiApplicationTests.java     # reescrito
payment-api/src/test/java/com/orderslab/payment_api/PaymentApiApplicationTests.java # reescrito
invoice-api/src/test/java/com/orderslab/invoice_api/InvoiceApiApplicationTests.java # reescrito
```

**Structure Decision**: reescrita in-place dos 3 arquivos placeholder já existentes — não
cria pacote/arquivo novo (diferente dos itens 1.6/1.7, que criaram pacotes `service`/
`controller` novos sob `src/test/java`).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
