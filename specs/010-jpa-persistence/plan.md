# Implementation Plan: Persistência JPA Simétrica

**Branch**: `010-jpa-persistence` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-jpa-persistence/spec.md`

## Summary

Substituir o armazenamento em memória (`ConcurrentHashMap`) dos 3 serviços por persistência
JPA real: entidades anotadas diretamente nas classes `Model` existentes, `Repository` Spring
Data JPA injetado no `Service`, migração Flyway definindo o schema, `application.properties`
ativado apontando para o banco dedicado de cada serviço (item 1.9). Testes de Service (item
1.6) adaptados para Mockito. Ver [research.md](./research.md) para as 6 decisões técnicas e
[data-model.md](./data-model.md) para o schema exato.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: `spring-data-jpa`, `postgresql` (driver), `flyway-core` +
`flyway-database-postgresql` (v12.4.0, gerenciada pelo BOM) — novas nos 3 serviços

**Storage**: PostgreSQL real (bancos dedicados já provisionados pelo item 1.9)

**Testing**: testes de Service (item 1.6) adaptados para Mockito; testes de Controller (item
1.7) inalterados (já mockavam o Service); testes de integração (item 1.8) passam a exigir
Postgres real rodando — lacuna aceita, ver research.md Decisão 6

**Target Platform**: JVM (mesmo ambiente atual)

**Project Type**: 3 serviços backend — edição simétrica em cada um

**Performance Goals**: N/A

**Constraints**: contrato HTTP MUST permanecer idêntico (nenhuma mudança em Controller/
`GlobalExceptionHandler`/DTOs); implementação independente por serviço (Princípio I); schema
gerenciado só por migração, nunca por `ddl-auto` (Princípio II — real, não meio-termo)

**Scale/Scope**: 3 × (pom.xml + Model entity + Repository novo + migration nova +
application.properties + Service + ServiceTest) = ~21 arquivos tocados

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Mesmo padrão replicado 3x, sem código/lib compartilhado entre serviços; cada um no seu próprio banco (item 1.9). **PASS** |
| II. Funcionalidade Técnica Real | Sim | É exatamente o que este princípio pede desde a v3.0.0 — persistência real, não mais "meio-termo" em memória. **PASS** |
| III. Persistência Real desde a Fase 1 | Sim | Esta feature É a implementação formal do Princípio III — fecha a lacuna que a v3.0.0 da constitution já declarava obrigatória. **PASS** |
| IV. Portabilidade Real para Qualquer Nuvem | Sim | PostgreSQL padrão, sem recurso proprietário de nuvem — portável para qualquer provedor gerenciado. **PASS** |
| V, VI, VII | Não aplicável | Feature não toca IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `data-model.md` confirma que nenhum relacionamento
cross-database é criado (preservando Princípio I); nenhuma mudança de contrato HTTP — segue
**PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/010-jpa-persistence/
├── plan.md              # This file
├── research.md          # Phase 0 output — 6 decisões técnicas
├── data-model.md         # Phase 1 output — schema exato das 3 tabelas
├── quickstart.md         # Phase 1 output — validação de persistência real
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `contracts/` novo — nenhuma mudança de contrato HTTP; os contratos de erro já
documentados nos itens 1.3-1.5 continuam valendo sem alteração.

### Source Code (repository root)

```text
order-api/pom.xml                                                    # editado
order-api/src/main/java/.../model/Order.java                          # + @Entity
order-api/src/main/java/.../repository/OrderRepository.java           # novo
order-api/src/main/resources/db/migration/V1__create_orders_table.sql # novo
order-api/src/main/resources/application.properties                  # editado
order-api/src/main/java/.../service/OrderService.java                 # editado (Repository)
order-api/src/test/java/.../service/OrderServiceTest.java             # editado (Mockito)

payment-api/ (mesmo padrão: pom.xml, model/Payment.java,
  repository/PaymentRepository.java, db/migration/V1__create_payments_table.sql,
  application.properties, service/PaymentService.java,
  test/.../service/PaymentServiceTest.java)

invoice-api/ (mesmo padrão: pom.xml, model/Invoice.java,
  repository/InvoiceRepository.java, db/migration/V1__create_invoices_table.sql,
  application.properties, service/InvoiceService.java,
  test/.../service/InvoiceServiceTest.java)
```

**Structure Decision**: novo pacote `repository` por serviço (paralelo a `service`/
`controller`/`exception`/`model` já existentes); migrations em `src/main/resources/db/migration/`
(convenção padrão do Flyway, detectada automaticamente sem configuração extra).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
