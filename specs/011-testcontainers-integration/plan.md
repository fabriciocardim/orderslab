# Implementation Plan: Testes de Integração com Testcontainers

**Branch**: `011-testcontainers-integration` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-testcontainers-integration/spec.md`

## Summary

Fechar a lacuna de teste aceita no item 1.10 (FR-008): os 3 `*ApplicationTests.java` (item
1.8) passam a subir um container PostgreSQL efêmero via Testcontainers em vez de exigir um
Postgres real já rodando em `localhost:5432`. `@ServiceConnection` injeta automaticamente
`spring.datasource.*` no contexto de teste; a mesma migração Flyway do item 1.10 roda contra
o container efêmero. Nenhuma mudança em produção, Service, Controller ou testes que não usam
banco. Ver [research.md](./research.md) para as 5 decisões técnicas (incluindo a renomeação
de artifacts do Testcontainers 2.x, confirmada empiricamente).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies** (escopo `test`, novas nos 3 serviços): `spring-boot-testcontainers`
(módulo direto, não `-starter-`), `testcontainers-postgresql`, `testcontainers-junit-jupiter`
(Testcontainers 2.0.5, gerenciado pelo BOM `spring-boot-dependencies:4.1.1`)

**Storage**: PostgreSQL efêmero (`postgres:15-alpine`, mesma imagem de
`infra/docker-compose.yml`), criado e descartado pelo Testcontainers por execução de classe
de teste — só para os testes; produção continua usando o Postgres real do item 1.9

**Testing**: `*ApplicationTests.java` (item 1.8) reescritos para usar `@Testcontainers` +
`@Container` + `@ServiceConnection`; `*ServiceTest.java` (1.6) e `*ControllerTest.java` (1.7)
inalterados, sem tocar em Docker

**Target Platform**: JVM (mesmo ambiente atual) + runtime Docker disponível na máquina que
roda os testes

**Project Type**: 3 serviços backend — edição simétrica em cada um

**Performance Goals**: N/A

**Constraints**: configuração de produção (`application.properties`, Compose, k8s) MUST
permanecer inalterada (FR-005); mesma major version de Postgres em teste e produção (FR-004);
testes que não usam banco (1.6, 1.7) MUST continuar sem dependência de Docker (FR-008)

**Scale/Scope**: 3 × (`pom.xml` editado + `*ApplicationTests.java` editado) = 6 arquivos
tocados, nenhum arquivo novo de produção

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Cada serviço sobe seu próprio container de teste isolado, sem estado/infra compartilhada entre os 3 — mesmo padrão de independência já usado em produção (item 1.9). **PASS** |
| II. Funcionalidade Técnica Real | Sim | Os testes passam a validar contra um Postgres real (mesma imagem de produção) rodando a migração Flyway real — mais rigoroso que a lacuna anterior, não menos. **PASS** |
| III. Persistência Real desde a Fase 1 | Sim | Reforça o princípio também em teste: nenhum atalho tipo `ddl-auto`/H2 em memória — o schema de teste nasce da mesma migração usada em produção (item 1.10). **PASS** |
| IV. Portabilidade Real para Qualquer Nuvem | Sim | Testcontainers não introduz nenhum lock-in de provedor — roda em qualquer máquina/CI com Docker disponível. **PASS** |
| V, VI, VII | Não aplicável | Feature não toca IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: nenhuma mudança de contrato HTTP, nenhuma mudança em
produção — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/011-testcontainers-integration/
├── plan.md              # This file
├── research.md          # Phase 0 output — 5 decisões técnicas
├── quickstart.md         # Phase 1 output — validação de execução self-contained
├── spec.md
└── tasks.md              # Phase 2 output (/speckit-tasks command)
```

Sem `data-model.md`/`contracts/` — nenhuma entidade nova, nenhuma mudança de contrato HTTP;
feature puramente de infraestrutura de teste.

### Source Code (repository root)

```text
order-api/pom.xml                                                          # editado (3 deps de teste)
order-api/src/test/java/.../OrderApiApplicationTests.java                   # editado (Testcontainers)

payment-api/pom.xml                                                         # mesmo padrão
payment-api/src/test/java/.../PaymentApiApplicationTests.java               # mesmo padrão

invoice-api/pom.xml                                                         # mesmo padrão
invoice-api/src/test/java/.../InvoiceApiApplicationTests.java               # mesmo padrão
```

**Structure Decision**: nenhum pacote/arquivo novo — a mudança é inteiramente dentro dos
arquivos já existentes dos itens 1.8 (`*ApplicationTests.java`) e 1.10/1.1 (`pom.xml`).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
