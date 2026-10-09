# Tasks: Decisão de Ownership de Schema/DB

**Input**: Design documents from `/specs/009-db-ownership-decision/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: validação empírica via `docker compose` + `psql` (ver `quickstart.md`); sem
testes automatizados Java, já que nenhum código de aplicação é tocado.

## Fase 1: Setup — não aplicável

## Fase 2: Foundational

- [X] T001 Criar `infra/postgres-init/init-databases.sql` — `CREATE DATABASE`/
      `CREATE USER`/`GRANT`/`ALTER DATABASE ... OWNER TO` para os 3 serviços (`order_db`/
      `order_user`, `payment_db`/`payment_user`, `invoice_db`/`invoice_user`), conforme
      tabela em `research.md` (Decisão 2). Usado tanto pelo Compose (T002) quanto pelo
      `ConfigMap` do k8s (T003).

**Checkpoint**: script de provisionamento pronto para ser montado nos dois ambientes.

---

## Fase 3: User Story 1 - Cada serviço tem seu próprio banco e credencial (Priority: P1) 🎯 MVP

**Goal**: 3 bancos + 3 usuários dedicados provisionados automaticamente; os 3 serviços (nos
2 ambientes) apontam para o seu próprio banco/usuário.

**Independent Test**: `docker compose up -d postgres-api` + `psql` confirmando bancos/
usuários e isolamento entre eles (ver `quickstart.md`).

### Implementation for User Story 1

- [X] T002 [P] [US1] Editar `infra/docker-compose.yml`: serviço `postgres-api` ganha
      `volumes:` montando `T001` em `/docker-entrypoint-initdb.d/`; `POSTGRES_USER`/
      `POSTGRES_PASSWORD` renomeados para um usuário administrativo (`apis_admin`); os 3
      serviços (`order-service`/`payment-service`/`invoice-service`) com
      `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` atualizados para o banco/usuário
      dedicado de cada um (depende de T001)
- [X] T003 [P] [US1] Criar `infra/k8s/postgres-api-init-configmap.yaml` — `ConfigMap` com o
      mesmo conteúdo SQL de T001 (depende de T001)
- [X] T004 [US1] Editar `infra/k8s/postgres-api-deployment.yaml`: montar o `ConfigMap` de
      T003 como volume em `/docker-entrypoint-initdb.d/`; renomear
      `POSTGRES_USER`/`POSTGRES_PASSWORD` para o usuário administrativo, mesmo padrão de T002
      (depende de T003)
- [X] T005 [P] [US1] Editar `infra/k8s/order-service-deployment.yaml` — env vars de
      datasource apontando para `order_db`/`order_user`
- [X] T006 [P] [US1] Editar `infra/k8s/payment-service-deployment.yaml` — env vars de
      datasource apontando para `payment_db`/`payment_user`
- [X] T007 [P] [US1] Editar `infra/k8s/invoice-service-deployment.yaml` — env vars de
      datasource apontando para `invoice_db`/`invoice_user`
- [X] T008 [US1] Validar localmente via Docker Compose (`quickstart.md`): subir só
      `postgres-api`, confirmar os 3 bancos/usuários via `psql`, confirmar que cada usuário
      só acessa o próprio banco (depende de T002; requer o Docker daemon rodando)
- [X] T009 [US1] Revisar os manifests k8s editados/criados (T003-T007): sintaxe YAML válida
      e, se um cluster estiver disponível, `kubectl apply --dry-run=client -f infra/k8s/`
      (depende de T003, T004, T005, T006, T007)

**Checkpoint**: provisionamento confirmado no Compose; manifests k8s revisados.

---

## Fase Final: Polish & Cross-Cutting Concerns

- [X] T010 [P] Rodar `./mvnw clean verify` nos 3 serviços Java — confirmar que continuam
      passando sem nenhuma regressão (SC-004; nenhum código Java foi tocado)
- [X] T011 [P] Atualizar o item 1.9 de `ROADMAP.md` como concluído
- [X] T012 Commitar os arquivos de infra novos/editados e `specs/009-db-ownership-decision/`
      na branch `feature/S001_incluirSDD`

---

## Dependencies & Execution Order

- T001 (Foundational) bloqueia T002 e T003.
- T002 é independente de T003-T007 (Compose vs. k8s, arquivos diferentes).
- T004 depende de T003. T005/T006/T007 são independentes entre si e de T003/T004.
- T008 depende de T002 (precisa do Compose editado e do Docker rodando).
- T009 depende de T003-T007 completos.
- Polish depende de T008 e T009.

## Implementation Strategy

### MVP First (única user story)

1. T001 (Foundational).
2. T002 e (T003→T004, T005, T006, T007) em paralelo.
3. T008 (validação Compose) e T009 (revisão k8s).
4. **STOP and VALIDATE**: se T008 confirma isolamento e T009 não encontra erro de sintaxe, a
   feature está pronta.

## Notes

- T008 depende do Docker daemon estar rodando — se não estiver, perguntar ao usuário antes
  de tentar iniciar, ou seguir só com revisão estática do SQL/YAML e registrar a limitação.
- Nenhum arquivo Java (`src/main`, `src/test`, `pom.xml`) é tocado por esta feature.
