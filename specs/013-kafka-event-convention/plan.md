# Implementation Plan: Convenção de Evento/Tópico Kafka

**Branch**: `013-kafka-event-convention` | **Date**: 2026-09-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-kafka-event-convention/spec.md`

## Summary

Documentar (não implementar) o contrato de eventos Kafka que os itens E2.2-E2.4 seguirão:
convenção de nome de tópico (`<domínio>.<evento>`), envelope obrigatório de payload
(`eventId`/`eventType`/`eventVersion`/`occurredAt`/`orderId`) e o mecanismo de serialização
correto para a versão real do Spring Kafka em uso. Ver [research.md](./research.md) para as
4 decisões técnicas e [contracts/event-contract.md](./contracts/event-contract.md) para o
contrato completo, consumido diretamente pelos 3 itens seguintes.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: nenhuma nova — `spring-kafka:4.1.1` já presente transitivamente
via `spring-boot-starter-kafka` nos 3 serviços desde o item 1.1; contém tanto o serializer
clássico (Jackson 2) quanto o novo `JacksonJsonSerializer`/`JacksonJsonDeserializer`
(Jackson 3) — este último é o escolhido (research.md Decisão 1)

**Storage**: N/A

**Testing**: N/A — nenhum código novo, validação é documental (`quickstart.md`)

**Target Platform**: N/A (decisão de contrato, não implementação)

**Project Type**: decisão documentada, consumida pelos 3 serviços de forma independente nos
itens seguintes (E2.2-E2.4)

**Performance Goals**: N/A

**Constraints**: a convenção MUST ser implementável sem nenhuma biblioteca/dependência de
código compartilhada entre os 3 serviços (FR-004, Princípio I); `orderId` MUST estar presente
em todo evento, mesmo os que não são diretamente sobre um pedido (FR-001)

**Scale/Scope**: 0 arquivos de produção tocados — só documentação em
`specs/013-kafka-event-convention/`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | O contrato é documentado, não uma lib compartilhada — cada serviço implementa sua própria classe de evento seguindo a convenção, sem dependência de código comum (FR-004). **PASS** |
| II. Funcionalidade Técnica Real | Sim | O mecanismo de serialização escolhido (`JacksonJsonSerializer`/`Deserializer`) é confirmado real via inspeção de bytecode, consistente com a decisão Jackson 3 já em vigor desde o item 1.7 — não uma solução "de teste" ou simplificada. **PASS** |
| VI. Observabilidade como Requisito de Primeira Classe | Sim | O campo `orderId` obrigatório em todo evento é exatamente o que permite, mais adiante (Fase 6), correlacionar eventos a um trace/transação de negócio — esta decisão prepara terreno sem antecipar escopo. **PASS** |
| III, IV, V, VII | Não aplicável | Feature não toca persistência, portabilidade de infra, IAM nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: o contrato final (`contracts/event-contract.md`) confirma
zero dependência de código compartilhada e a presença obrigatória de `orderId` nos 7 eventos
— segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/013-kafka-event-convention/
├── plan.md              # This file
├── research.md          # Phase 0 output — 4 decisões técnicas
├── contracts/
│   └── event-contract.md # Phase 1 output — o contrato em si, consumido por E2.2-E2.4
├── quickstart.md         # Phase 1 output — validação documental
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md` — o "modelo de dados" desta feature é o próprio envelope de evento,
já descrito por completo em `contracts/event-contract.md` (evitar duplicar a mesma
informação em dois arquivos, Princípio II).

### Source Code (repository root)

```text
(nenhum arquivo de produção tocado por esta feature)
```

**Structure Decision**: feature puramente documental — nenhum `pom.xml`/código dos 3
serviços é alterado. O contrato em `contracts/event-contract.md` é a entrada dos itens
E2.2 (`order-api`), E2.3 (`payment-api`) e E2.4 (`invoice-api`), sequenciados a seguir.

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
