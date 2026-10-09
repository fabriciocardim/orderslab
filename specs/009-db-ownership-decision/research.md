# Research: Decisão de Ownership de Schema/DB

## Decisão 1: Mecanismo de provisionamento

**Decision**: script SQL único (`CREATE DATABASE`/`CREATE USER`/`GRANT` para os 3 serviços),
executado automaticamente pelo mecanismo oficial da imagem `postgres`
(`/docker-entrypoint-initdb.d/`), que roda scripts `.sql`/`.sh` encontrados nesse diretório
**apenas na primeira inicialização** do volume de dados do container (volume vazio).

**Rationale**: é o mecanismo padrão e documentado da imagem oficial, sem precisar de
ferramenta adicional. `CREATE DATABASE`/`CREATE ROLE`/`GRANT` são comandos de nível de
cluster (não dependem de qual "banco atual" o script está conectado) — um único arquivo
`.sql` basta, não é necessário um script `.sh` com lógica de conexão por banco.

**Observação sobre o volume de dados atual**: o `docker-compose.yml` de hoje não declara
nenhum volume nomeado para `postgres-api` (dados vivem no filesystem efêmero do container)
— então o script de init roda a cada `docker compose up` depois de um `down` (sem `-v`
seria necessário se um volume nomeado existisse; hoje nem isso é preciso). Isso é
conveniente para o laboratório (estado sempre limpo), mas também significa que os dados são
perdidos a cada `docker compose down` — comportamento já existente, não alterado por esta
feature.

**Alternatives considered**: ferramenta de migração (Flyway/Liquibase) para criar os bancos:
rejeitado — essas ferramentas migram *schema dentro de* um banco já existente (tabelas), não
criam bancos/roles a nível de cluster; e introduzi-las agora seria antecipar o item 1.10 sem
necessidade.

## Decisão 2: Nomes de banco/usuário e privilégios

**Decision**:

| Serviço | Banco | Usuário | Privilégios |
|---|---|---|---|
| order-api | `order_db` | `order_user` | `ALL PRIVILEGES` em `order_db`; `OWNER` do banco |
| payment-api | `payment_db` | `payment_user` | `ALL PRIVILEGES` em `payment_db`; `OWNER` do banco |
| invoice-api | `invoice_db` | `invoice_user` | `ALL PRIVILEGES` em `invoice_db`; `OWNER` do banco |

O usuário/role de bootstrap do container (definido via `POSTGRES_USER`/`POSTGRES_PASSWORD`,
o superusuário que roda o script de init) passa a ser um usuário administrativo
(`apis_admin`) separado dos 3 usuários de aplicação — nenhum dos 3 serviços usa mais esse
usuário administrativo diretamente.

**Rationale**: `OWNER` do próprio banco (não só `GRANT ALL PRIVILEGES`) garante que o
usuário do serviço também pode criar/alterar tabelas nesse banco sem depender do
administrador — necessário para quando o item 1.10 ligar JPA/`ddl-auto`/Flyway.

**Achado durante a implementação (validado empiricamente)**: `CREATE DATABASE` no Postgres
concede `CONNECT` a `PUBLIC` por padrão em todo banco novo — sem revogar isso
explicitamente, qualquer usuário conseguia conectar em qualquer banco (confirmado ao testar
`order_user` contra `payment_db` antes da correção: a conexão funcionava, quando deveria
falhar). Corrigido adicionando `REVOKE CONNECT ON DATABASE <banco> FROM PUBLIC;` logo após
o `ALTER DATABASE ... OWNER TO` de cada banco. Reforça por que a validação empírica (não só
a leitura do SQL) importa: o script "parecia" correto e só a tentativa real de conexão
cruzada revelou o problema.

## Decisão 3: Docker Compose — volume montado

**Decision**: arquivo `infra/postgres-init/init-databases.sql`, montado via `volumes:` no
serviço `postgres-api` do `docker-compose.yml`:

```yaml
volumes:
  - ./postgres-init/init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql:ro
```

## Decisão 4: Kubernetes — ConfigMap montado

**Decision**: novo manifest `infra/k8s/postgres-api-init-configmap.yaml` (`ConfigMap` com o
mesmo conteúdo SQL como um `data` entry), montado como volume no
`postgres-api-deployment.yaml` no caminho `/docker-entrypoint-initdb.d/`.

**Rationale**: replica o mesmo mecanismo do Compose — a imagem `postgres` no pod do
Kubernetes se comporta da mesma forma (roda scripts encontrados nesse diretório na primeira
inicialização). Manter o SQL como `ConfigMap` (não hardcoded inline no Deployment) segue a
mesma prática de configuração como código já usada no restante do `infra/k8s/`.

## Decisão 5: Atualização das env vars dos 3 serviços

**Decision**: em `docker-compose.yml` e nos 3 `infra/k8s/*-service-deployment.yaml`,
substituir:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-api:5432/apisdb` → banco dedicado
  (ex.: `.../order_db` para order-service)
- `SPRING_DATASOURCE_USERNAME=apis_user` → usuário dedicado (ex.: `order_user`)
- `SPRING_DATASOURCE_PASSWORD=apis_password` → senha dedicada (ex.: `order_password`)

**Rationale**: já deixa a infra pronta para o item 1.10 — quando o datasource for
descomentado em `application.properties`, essas variáveis de ambiente já vão estar corretas,
sem precisar mexer em infra de novo.

## Conclusão

Arquivos tocados: `infra/docker-compose.yml` (editado), novo
`infra/postgres-init/init-databases.sql`, `infra/k8s/postgres-api-deployment.yaml` (editado,
+ volume do ConfigMap), novo `infra/k8s/postgres-api-init-configmap.yaml`, e os 3
`infra/k8s/*-service-deployment.yaml` (editados, env vars). Nenhum arquivo Java tocado.
