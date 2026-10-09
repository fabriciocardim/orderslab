# Implementation Plan: Decisão de Ownership de Schema/DB

**Branch**: `009-db-ownership-decision` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-db-ownership-decision/spec.md`

## Summary

Provisionar 3 bancos + 3 usuários dedicados (um por serviço) dentro da instância Postgres
compartilhada, via script SQL montado no mecanismo `/docker-entrypoint-initdb.d/` (Compose:
volume; k8s: ConfigMap), e atualizar as env vars de datasource dos 3 serviços em ambos os
manifests para apontar ao banco/usuário correto. Ver [research.md](./research.md) para as
decisões técnicas completas. Nenhum código Java é alterado — datasource continua comentado
até o item 1.10.

## Technical Context

**Language/Version**: SQL (PostgreSQL DDL/DCL), YAML (Compose + k8s manifests)

**Primary Dependencies**: imagem `postgres:15-alpine` já em uso (item de infra pré-existente);
nenhuma dependência nova

**Storage**: PostgreSQL — 3 bancos lógicos novos dentro da instância já existente

**Testing**: validação empírica subindo só o `postgres-api` (Compose) e inspecionando via
`psql`/`pg_isready` (ver quickstart.md); revisão estática dos manifests k8s (sem cluster
disponível para validação ao vivo nesta sessão)

**Target Platform**: Docker Compose local + Kubernetes (Docker Desktop)

**Project Type**: infraestrutura como código — sem alteração de código de aplicação

**Performance Goals**: N/A

**Constraints**: instância Postgres MUST continuar única (não 3 containers); nenhuma mudança
em `application.properties`/`pom.xml`/entidades Java; implementação independente por serviço
nas credenciais (Princípio I), mesmo compartilhando a instância física

**Scale/Scope**: 1 arquivo SQL novo, 1 `ConfigMap` novo, 5 manifests editados
(`docker-compose.yml` + `postgres-api-deployment.yaml` + 3 `*-service-deployment.yaml`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Aplica-se? | Avaliação |
|---|---|---|
| I. Independência dos Serviços | Sim | Fecha a lacuna identificada: hoje os 3 serviços compartilham banco/usuário por baixo do capô, apesar de independentes em código/build/deploy. Esta feature estende a independência à camada de persistência. **PASS** |
| III. Persistência Real desde a Fase 1 | Sim (preparação) | Não liga persistência ainda (isso é o item 1.10) — só prepara a infra, consistente com a sequência já definida no ROADMAP. **PASS** |
| IV. Portabilidade Real para Qualquer Nuvem | Sim | Múltiplos bancos numa instância Postgres é suportado por qualquer provedor gerenciado (RDS, Cloud SQL, etc.) — não introduz dependência de um provedor específico. **PASS** |
| II, V, VI, VII | Não aplicável | Feature não toca lógica de negócio, IAM, observabilidade nem agentes de SRE. |

Nenhuma violação. Gate passa sem necessidade de Complexity Tracking.

**Re-check pós-design (Fase 1)**: `research.md` confirma que o design não introduz
dependência de provedor nem toca código de aplicação — segue **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/009-db-ownership-decision/
├── plan.md              # This file
├── research.md          # Phase 0 output — decisões completas
├── quickstart.md         # Phase 1 output — como validar
└── spec.md
```

Sem `tasks.md` gerado ainda (próxima fase) e sem `data-model.md`/`contracts/` — não há
entidades JPA nem interface HTTP nova; o "modelo" desta feature é a tabela de
banco/usuário/privilégios já documentada em `research.md` (Decisão 2).

### Source Code (repository root)

```text
infra/postgres-init/init-databases.sql          # novo
infra/docker-compose.yml                        # editado (postgres-api + 3 serviços)
infra/k8s/postgres-api-init-configmap.yaml       # novo
infra/k8s/postgres-api-deployment.yaml           # editado (+ volume do ConfigMap)
infra/k8s/order-service-deployment.yaml          # editado (env vars)
infra/k8s/payment-service-deployment.yaml        # editado (env vars)
infra/k8s/invoice-service-deployment.yaml        # editado (env vars)
```

**Structure Decision**: novo diretório `infra/postgres-init/` para o script SQL (paralelo à
convenção já usada — `infra/k8s/` para manifests k8s, `infra/docker-compose.yml` na raiz de
`infra/`).

## Complexity Tracking

*Não aplicável — nenhuma violação de constitution identificada.*
