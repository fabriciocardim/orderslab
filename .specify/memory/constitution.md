<!--
Sync Impact Report
- Version change: (none) → 1.0.0 (initial ratification)
- Modified principles: n/a (initial version)
- Added principles:
  - I. Independência dos Serviços
  - II. Simulação de Domínio sem Lógica de Negócio Real
  - III. Estado em Memória (Fase Inicial, Sem Persistência)
  - IV. Prontidão para Nuvem (Cloud-Native desde o Início)
  - V. Segurança e IAM Centralizados (Keycloak)
- Added sections: Padrões de Qualidade e CI/CD; Fases de Evolução do Laboratório; Governance
- Removed sections: none
- Templates requiring updates: n/a — this command only touches the constitution itself
  (Scope Guard); dependent templates read it at runtime and were not modified.
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): não existe uma data formal anterior de ratificação — os
    princípios já existiam informalmente no README.md (seção 3), mas nunca haviam sido
    codificados como constitution. Usamos a data desta primeira emissão formal
    (2026-09-24) como data de ratificação.
-->

# orderslab Constitution

## Core Principles

### I. Independência dos Serviços
Cada microsserviço (`order-api`, `payment-api`, `invoice-api`) MUST ser um projeto Spring
Boot autocontido, com seu próprio `pom.xml`, ciclo de vida Maven, `Dockerfile` e pipeline de
CI/CD. Nenhum serviço MUST depender de classes ou pacotes internos de outro serviço; toda
comunicação entre serviços MUST ocorrer por contrato externo (REST síncrono hoje; Kafka
assíncrono a partir da Fase 2). Não existe `pom.xml` raiz nem parent Maven interno
compartilhado — cada serviço herda diretamente de `spring-boot-starter-parent`.

**Rationale**: garante que os três serviços evoluam, sejam versionados (tags
`<service>-vX.Y.Z`) e implantados em ritmos independentes, refletindo a proposta do
laboratório de simular microsserviços realistas.

### II. Simulação de Domínio sem Lógica de Negócio Real
`payment-api` e `invoice-api` MUST permanecer simuladores de processo, não sistemas de
negócio completos: eles processam e retornam se um pagamento/nota foi "efetivado" ou não,
sem regras de negócio complexas (cálculo de impostos, antifraude, etc.). Qualquer lógica de
negócio real introduzida nesses serviços MUST ser tratada como mudança de escopo do
laboratório — a ser declarada explicitamente em `/speckit-specify` — não como um bug fix ou
melhoria incremental.

**Rationale**: o laboratório existe para estudar comunicação e infraestrutura entre
microsserviços, não para modelar domínios de pagamento/fiscal reais.

### III. Estado em Memória (Fase Inicial, Sem Persistência)
Na Fase 1 atual, os três microsserviços MUST manter estado apenas em memória (ex.:
`ConcurrentHashMap`), sem depender de um banco de dados relacional para funcionar.
Dependências de banco (ex.: driver Postgres em `pom.xml`) e infraestrutura de banco (ex.:
serviço `postgres-api` em `infra/docker-compose.yml`/`infra/k8s/`) PODEM já existir em
preparação, mas a configuração de datasource (`spring.datasource.*`) MUST permanecer
desabilitada/comentada em `application.properties` até que a evolução do laboratório
introduza persistência formalmente.

**Rationale**: mantém o foco estrito na comunicação entre serviços antes de introduzir
complexidade de persistência, evitando acoplamento prematuro a um schema de banco.

### IV. Prontidão para Nuvem (Cloud-Native desde o Início)
Todo serviço MUST ser conteinerizável via `Dockerfile` multi-stage e implantável pelos
manifests Kubernetes equivalentes em `infra/k8s/` (Deployment + Service dedicados, no
namespace `orderslab`). Mudanças de infraestrutura local (`infra/docker-compose.yml`) que
afetem o comportamento de um serviço MUST ser refletidas nos manifests k8s correspondentes,
e vice-versa — mesmo que a sincronização não seja automática (os manifests atuais já
divergem intencionalmente do `kompose convert` original, ver README seção 5.1).

**Rationale**: o objetivo declarado do laboratório é migração futura para nuvem; manter os
dois modos de execução (Compose local e Kubernetes) coerentes evita que o laboratório vire
só um exercício de Docker Compose.

### V. Segurança e IAM Centralizados (Keycloak)
Quando a camada de segurança for introduzida (Fase 5), autenticação e autorização MUST ser
centralizadas via Keycloak como Identity Provider único, integrado a todos os microsserviços
e ao frontend — nenhum serviço MUST implementar mecanismo de autenticação paralelo próprio.
Até lá, o bloco Keycloak permanece desativado/comentado em `infra/docker-compose.yml`.

**Rationale**: evita duplicação de lógica de autenticação por serviço e mantém um único
ponto de verdade para identidade, consistente com a proposta do laboratório de estudar
segurança corporativa com IdP.

## Padrões de Qualidade e CI/CD

Cada serviço MUST manter seu próprio workflow de gatilho (`order-api.yml`,
`payment-api.yml`, `invoice-api.yml`), todos delegando os jobs reais ao workflow reusável
[`service-ci.yml`](.github/workflows/service-ci.yml) — mudanças nos jobs (`build-and-test`,
`quality`/PMD, `security`/CodeQL, `package`) MUST ser feitas nesse arquivo único, nunca
duplicadas por serviço.

Todo serviço MUST declarar o `maven-pmd-plugin` com os rulesets `bestpractices` +
`errorprone`, e o job `quality` MUST publicar `target/pmd.xml` como artifact
independentemente do resultado. Todo serviço MUST ser analisado pelo CodeQL (job
`security`) antes de release; o job `package` (build da imagem Docker) MUST só rodar após
`build-and-test`, `quality` e `security` passarem.

Releases MUST usar tags por serviço no formato `<service>-vX.Y.Z` (ex.: `order-api-v1.0.0`),
nunca uma tag global do monorepo — isso preserva a Independência dos Serviços (Princípio I)
também no versionamento.

## Fases de Evolução do Laboratório

O laboratório evolui em fases sequenciais e cumulativas (ver README seção 7):

1. **Fase 1 (atual)**: estrutura base, comunicação síncrona REST, sem persistência,
   conteinerização Docker.
2. **Fase 2**: comunicação assíncrona via Apache Kafka (broker já disponível; produção e
   consumo de mensagens ainda não implementados pelos serviços).
3. **Fase 3**: orquestração de fluxos com Apache Airflow.
4. **Fase 4 (em andamento)**: implantação e validação em Kubernetes local.
5. **Fase 5**: segurança de ponta a ponta com Keycloak.

Uma feature que introduza capacidade de uma fase futura antes do previsto (ex.: ligar
persistência antes da Fase 3, ou autenticação antes da Fase 5) MUST declarar esse
adiantamento explicitamente em `/speckit-specify` e `/speckit-plan`, para que o desvio da
constitution vigente seja uma decisão consciente, não um efeito colateral silencioso.

## Governance

Esta constitution supera qualquer prática ou convenção não escrita do laboratório. Toda
spec (`/speckit-specify`), plano (`/speckit-plan`) ou lista de tarefas (`/speckit-tasks`)
gerada pelo Spec Kit MUST ser consistente com os princípios acima; divergências MUST ser
resolvidas emendando esta constitution antes de prosseguir com a implementação, não
ignoradas silenciosamente.

Emendas a este documento seguem versionamento semântico (MAJOR.MINOR.PATCH): remoção ou
redefinição incompatível de um princípio é MAJOR; adição de um novo princípio ou seção é
MINOR; correções de redação e esclarecimentos são PATCH. Toda emenda MUST atualizar
`Last Amended` e ser registrada no Sync Impact Report gerado por `/speckit-constitution`.

Revisão de conformidade acontece a cada `/speckit-plan`: o plano de implementação MUST citar
explicitamente qualquer desvio de um princípio e justificá-lo, ou ajustar o escopo para
respeitar a constitution vigente. Use [`README.md`](README.md) para instruções operacionais
(como rodar, testar e implantar) e esta constitution para princípios de governança do
laboratório.

**Version**: 1.0.0 | **Ratified**: 2026-09-24 | **Last Amended**: 2026-09-24
