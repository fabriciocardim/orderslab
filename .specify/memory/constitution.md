<!--
Sync Impact Report
- Version change: 1.0.0 → 2.0.0
- Modified principles (redefinition, not mere expansion — justifies MAJOR):
  - II. Simulação de Domínio sem Lógica de Negócio Real
    → II. Funcionalidade Técnica Real, Domínio de Negócio Simples
    (serviços deixam de poder ser stubs tecnicamente incompletos; fluxos técnicos agora
    MUST funcionar de ponta a ponta, mesmo com regras de negócio propositalmente simples)
  - IV. Prontidão para Nuvem (Cloud-Native desde o Início)
    → IV. Portabilidade Real para Qualquer Nuvem (Cloud-Agnostic)
    (de "arquitetado para migração futura" para requisito ativo de portabilidade/anti-lock-in)
- Added principles:
  - VI. Observabilidade como Requisito de Primeira Classe
  - VII. Governança de Remediação Autônoma (SRE)
- Added sections:
  - Preâmbulo de Missão e Visão de Longo Prazo (antes de "Core Principles")
  - Fase 6 (Observabilidade) e Fase 7 (SRE Autônomo) em "Fases de Evolução do Laboratório"
- Removed sections: none
- Follow-up TODOs:
  - A stack específica de observabilidade (Fase 6) e o desenho técnico dos agentes de SRE
    (Fase 7) ainda não estão definidos — serão detalhados via /speckit-specify quando essas
    fases forem iniciadas. Não é um placeholder pendente nesta constitution, é trabalho
    futuro fora do escopo desta emenda.
-->

# orderslab Constitution

Este laboratório existe para aprender e validar, na prática, conceitos de arquitetura de
software, infraestrutura e boas práticas de mercado. No momento desta emenda (2026-09-24), o
projeto tem apenas a arquitetura inicial e a estrutura de comunicação entre serviços
definidas — nenhuma funcionalidade de negócio está implementada ponta a ponta ainda. A partir
da adoção de Spec-Driven Development (SDD), o objetivo é dar robustez técnica ao projeto até
ter, ao final do laboratório, um sistema rodando de verdade e **plugável em qualquer
infraestrutura de nuvem** — servindo de base para testar ferramentas de observabilidade e
práticas de SRE (Site Reliability Engineering). O objetivo final do laboratório é ter uma
estrutura de SRE com **agentes autônomos** capazes de identificar problemas pelos pilares da
observabilidade (logs, métricas e traces) e propor correções via hotfixes ou Pull Requests de
forma automática (ver Princípio VII).

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

### II. Funcionalidade Técnica Real, Domínio de Negócio Simples
Os microsserviços MUST implementar fluxos técnicos reais e completos — persistência
funcionando de fato quando uma feature exigir, eventos publicados/consumidos corretamente no
Kafka, e APIs que respondem de ponta a ponta sem atalhos artificiais ou respostas
hard-coded. Isso deixa de ser "simulação" no sentido técnico. As regras de negócio em si,
porém, MUST permanecer propositalmente simples: `payment-api` e `invoice-api` decidem e
retornam sucesso/falha de forma simplificada, sem cálculo de imposto real, antifraude ou
qualquer lógica completa de domínio financeiro/fiscal. Qualquer ampliação real de domínio de
negócio MUST ser tratada como mudança de escopo do laboratório, declarada explicitamente em
`/speckit-specify`.

**Rationale**: o laboratório existe para validar arquitetura, infraestrutura e
observabilidade — não para modelar domínios de pagamento/fiscal reais. Mas o objetivo final
de "um sistema rodando de verdade" exige que os fluxos técnicos funcionem de ponta a ponta,
não apenas na aparência.

### III. Estado em Memória (Fase Inicial, Sem Persistência)
Na Fase 1 atual, os três microsserviços MUST manter estado apenas em memória (ex.:
`ConcurrentHashMap`), sem depender de um banco de dados relacional para funcionar.
Dependências de banco (ex.: driver Postgres em `pom.xml`) e infraestrutura de banco (ex.:
serviço `postgres-api` em `infra/docker-compose.yml`/`infra/k8s/`) PODEM já existir em
preparação, mas a configuração de datasource (`spring.datasource.*`) MUST permanecer
desabilitada/comentada em `application.properties` até que a evolução do laboratório
introduza persistência formalmente. Quando essa transição acontecer, ela MUST seguir o
Princípio II (funcionalidade técnica real, não um meio-termo simulado).

**Rationale**: mantém o foco estrito na comunicação entre serviços antes de introduzir
complexidade de persistência, evitando acoplamento prematuro a um schema de banco.

### IV. Portabilidade Real para Qualquer Nuvem (Cloud-Agnostic)
Todo serviço MUST continuar conteinerizável via `Dockerfile` multi-stage e implantável pelos
manifests Kubernetes em `infra/k8s/` (Deployment + Service dedicados, namespace
`orderslab`). Além disso, o projeto MUST poder ser implantado em qualquer provedor de nuvem
que ofereça um cluster Kubernetes padrão, sem depender de serviços proprietários de um
provedor específico sem uma camada de abstração explícita (ex.: broker Kafka genérico em vez
de um serviço gerenciado proprietário sem abstração). Mudanças de infraestrutura local
(`infra/docker-compose.yml`) que afetem o comportamento de um serviço MUST ser refletidas nos
manifests k8s correspondentes, e vice-versa.

**Rationale**: o objetivo final do laboratório é usar essa infraestrutura para testar
ferramentas de observabilidade e SRE de forma agnóstica de provedor — lock-in a um cloud
provider específico inviabilizaria esse objetivo.

### V. Segurança e IAM Centralizados (Keycloak)
Quando a camada de segurança for introduzida (Fase 5), autenticação e autorização MUST ser
centralizadas via Keycloak como Identity Provider único, integrado a todos os microsserviços
e ao frontend — nenhum serviço MUST implementar mecanismo de autenticação paralelo próprio.
Até lá, o bloco Keycloak permanece desativado/comentado em `infra/docker-compose.yml`.

**Rationale**: evita duplicação de lógica de autenticação por serviço e mantém um único
ponto de verdade para identidade, consistente com a proposta do laboratório de estudar
segurança corporativa com IdP.

### VI. Observabilidade como Requisito de Primeira Classe
Todo serviço MUST ser instrumentado desde sua criação ou alteração — não depois — cobrindo
os três pilares da observabilidade: logs estruturados, métricas e traces distribuídos. Uma
feature especificada via `/speckit-specify` que toque um serviço MUST considerar a
instrumentação de observabilidade como parte do escopo da própria feature, não como um débito
técnico aceitável para depois. A stack e as ferramentas específicas de observabilidade serão
escolhidas e formalizadas quando a Fase 6 for especificada, mas a exigência de instrumentar
os três pilares já vale a partir desta emenda.

**Rationale**: os agentes autônomos de SRE (Princípio VII) dependem inteiramente de
telemetria de qualidade; observabilidade adicionada retroativamente é sistematicamente pior
e mais cara do que observabilidade desde o design.

### VII. Governança de Remediação Autônoma (SRE)
Quando os agentes autônomos de SRE previstos para a Fase 7 forem implementados, eles PODEM
detectar problemas e diagnosticar causa raiz usando os pilares de observabilidade (Princípio
VI), e PODEM abrir automaticamente hotfixes ou Pull Requests propondo a correção, sem
necessidade de intervenção humana até esse ponto. Porém, o merge e o deploy final de
qualquer mudança gerada por esses agentes, em qualquer ambiente, MUST passar por aprovação
humana explícita antes de ser aplicado — não é permitido merge ou deploy totalmente
automático sem revisão humana. Essa trava MUST permanecer em vigor a menos que uma emenda
futura a remova explicitamente, com justificativa registrada no Sync Impact Report
correspondente.

**Rationale**: o laboratório está validando o conceito de remediação autônoma; manter
aprovação humana no ponto de maior risco (aplicar a mudança em produção) permite testar o
conceito com segurança, sem apostar a integridade do sistema na correção automática desde o
primeiro dia.

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
6. **Fase 6**: observabilidade — instrumentação dos três pilares (logs, métricas, traces) e
   integração com ferramentas de observabilidade, com o projeto já rodando em infraestrutura
   de nuvem plugável (ver Princípio IV).
7. **Fase 7**: SRE autônomo — implementação dos agentes autônomos que identificam problemas
   via observabilidade e abrem hotfixes/PRs automaticamente, com merge/deploy final ainda
   sob aprovação humana (ver Princípio VII).

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

**Version**: 2.0.0 | **Ratified**: 2026-09-24 | **Last Amended**: 2026-09-24
