<!--
Sync Impact Report
- Version change: 3.2.1 → 3.3.0
- Modified principles (mesma regra; só removidos detalhes de implementação, de fase e de
  histórico, e encurtados os rationales): I, II, III, IV, V, VI, VII
- Added sections: nenhuma
- Added rules (Governance): regra de adiantamento em relação ao ROADMAP.md; cláusula de
  escopo da constitution (só princípios e decisões duradouras)
- Removed sections: "Fases de Evolução do Laboratório" (o ROADMAP.md é o dono das fases);
  status datado na introdução
- Follow-up TODOs: nenhum
- Motivação: evitar emendas a cada mudança de status/fase; a constitution registra só
  princípios e decisões duradouras. Supera a emenda 3.2.1.
-->

# orderslab Constitution

Este laboratório existe para aprender e validar, na prática, conceitos de arquitetura de
software, infraestrutura e boas práticas de mercado. O objetivo é ter, ao final, um sistema
rodando de verdade e **plugável em qualquer infraestrutura de nuvem**, servindo de base para
testar ferramentas de observabilidade e práticas de SRE (Site Reliability Engineering). O
objetivo final é uma estrutura de SRE com **agentes autônomos** capazes de identificar
problemas pelos pilares da observabilidade (logs, métricas e traces) e propor correções via
hotfixes ou Pull Requests de forma automática (ver Princípio VII).

## Core Principles

### I. Independência dos Serviços
Cada microsserviço (`order-api`, `payment-api`, `invoice-api`) MUST ser um projeto
autocontido, com seu próprio build, ciclo de vida, imagem de contêiner e pipeline de CI/CD.
Nenhum serviço MUST depender de classes ou pacotes internos de outro; toda comunicação entre
serviços MUST ocorrer por contrato externo (REST ou eventos Kafka).

**Rationale**: permite que os serviços evoluam, sejam versionados e implantados em ritmos
independentes, como em microsserviços reais.

### II. Funcionalidade Técnica Real, Domínio de Negócio Simples
Os microsserviços MUST implementar fluxos técnicos reais e completos — persistência de
verdade, eventos publicados e consumidos corretamente no Kafka, APIs que respondem de ponta a
ponta, sem atalhos artificiais ou respostas hard-coded — e MUST validar toda entrada de API
antes de processá-la. As regras de negócio, porém, MUST permanecer propositalmente simples.
Qualquer ampliação real de domínio MUST ser declarada como mudança de escopo em
`/speckit-specify`.

**Rationale**: o laboratório valida arquitetura, infraestrutura e observabilidade, não
domínios financeiros ou fiscais; mas "rodando de verdade" exige fluxos técnicos reais.

### III. Persistência Real
Todo serviço MUST usar armazenamento persistente real (banco relacional Postgres) para o
estado de qualquer feature entregue; estado só em memória MUST NOT ser a implementação
entregue. Cada serviço MUST declarar sua própria dependência e configuração de persistência e
ser dono do seu próprio schema/tabelas, sem acessar tabelas de outro serviço, mesmo
compartilhando a mesma instância de banco.

**Rationale**: um serviço sem estado durável não sobrevive a restart nem pode ser observado
de forma realista; a independência do Princípio I vale também no nível de dados.

### IV. Portabilidade Real para Qualquer Nuvem (Cloud-Agnostic)
Todo serviço MUST ser conteinerizável e implantável em um cluster Kubernetes padrão, sem
depender de serviço proprietário de um provedor específico sem uma camada de abstração
explícita (ex.: Kafka genérico, não um serviço gerenciado proprietário). Mudança de
infraestrutura local que afete o comportamento de um serviço MUST ser refletida nos manifests
Kubernetes correspondentes, e vice-versa.

**Rationale**: observabilidade e SRE precisam ser testados de forma agnóstica de provedor;
lock-in inviabilizaria isso.

### V. Segurança e IAM Centralizados (Keycloak)
Quando a camada de segurança for introduzida, autenticação e autorização MUST ser
centralizadas no Keycloak como Identity Provider único, integrado a todos os microsserviços e
ao frontend; nenhum serviço MUST implementar mecanismo de autenticação paralelo.

**Rationale**: um único ponto de verdade para identidade evita duplicação de lógica de
autenticação e reflete segurança corporativa com IdP.

### VI. Observabilidade como Requisito de Primeira Classe (OpenTelemetry + SigNoz)
Todo serviço MUST ser instrumentado desde sua criação ou alteração — não depois — cobrindo
logs estruturados, métricas e traces distribuídos. O projeto MUST usar **OpenTelemetry** como
padrão único de instrumentação (OTLP) e **SigNoz** (self-hosted) como plataforma que consome
essa telemetria. Uma feature que toque um serviço MUST tratar sua instrumentação, mesmo
mínima, como parte do escopo da própria feature, não como débito para depois.

**Rationale**: os agentes de SRE (Princípio VII) dependem de telemetria de qualidade, e
observabilidade retroativa é pior e mais cara; um padrão aberto evita reinstrumentar quando o
backend mudar.

### VII. Governança de Remediação Autônoma (SRE)
Os agentes autônomos de SRE PODEM detectar problemas, diagnosticar causa raiz pelos pilares de
observabilidade (Princípio VI) e abrir hotfixes ou Pull Requests automaticamente. O merge e o
deploy de qualquer mudança gerada por eles, em qualquer ambiente, MUST passar por aprovação
humana explícita; merge ou deploy totalmente automático MUST NOT ser permitido. Essa trava só
pode ser removida por emenda que registre a justificativa.

**Rationale**: mantém aprovação humana no ponto de maior risco, permitindo testar remediação
autônoma sem apostar a integridade do sistema nela.

## Padrões de Qualidade e CI/CD

Cada serviço MUST ter seu próprio pipeline de gatilho, delegando os jobs reais a um workflow
reutilizável único; mudanças nos jobs MUST ser feitas nesse workflow, nunca duplicadas por
serviço. Todo serviço MUST passar por análise estática de código e por análise de segurança
antes de release, e o empacotamento da imagem MUST só ocorrer depois que build, testes e
essas análises passarem. Releases MUST usar tags por serviço no formato `<service>-vX.Y.Z`,
nunca uma tag global do monorepo.

## Governance

Esta constitution supera qualquer prática ou convenção não escrita do laboratório. Toda spec
(`/speckit-specify`), plano (`/speckit-plan`) ou lista de tarefas (`/speckit-tasks`) gerada
pelo Spec Kit MUST ser consistente com os princípios acima; divergências MUST ser resolvidas
emendando esta constitution antes de prosseguir, não ignoradas silenciosamente.

**Escopo**: esta constitution registra princípios e decisões duradouras. Status, fases,
versões de dependência, caminhos e nomes de arquivo MUST ficar no `ROADMAP.md`, no `README.md`
ou nas specs, nunca aqui.

**Adiantamento**: uma feature que antecipe capacidade fora da ordem do `ROADMAP.md` (ex.:
produzir eventos Kafka ou autenticação antes do previsto) MUST declarar esse adiantamento
explicitamente em `/speckit-specify` e `/speckit-plan`, para que o desvio seja uma decisão
consciente, não um efeito colateral silencioso.

**Emendas** seguem versionamento semântico (MAJOR.MINOR.PATCH): remoção ou redefinição
incompatível de um princípio é MAJOR; adição de princípio ou seção, ou ampliação material de
orientação, é MINOR; correções de redação e esclarecimentos são PATCH. Toda emenda MUST
atualizar `Last Amended` e ser registrada no Sync Impact Report gerado por
`/speckit-constitution`.

**Revisão de conformidade** acontece a cada `/speckit-plan`: o plano MUST citar
explicitamente qualquer desvio de um princípio e justificá-lo, ou ajustar o escopo para
respeitar a constitution vigente. O `README.md` traz as instruções operacionais (como rodar,
testar e implantar).

**Version**: 3.3.0 | **Ratified**: 2026-09-24 | **Last Amended**: 2026-10-04
