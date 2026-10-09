# Feature Specification: Instrumentação Básica com OpenTelemetry

**Feature Branch**: `012-otel-basic-instrumentation`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Instrumentação básica com OpenTelemetry nos 3 microsserviços (order-api, payment-api, invoice-api). Contexto: item 1.12, último item da Fase 1 do ROADMAP.md, servindo de gancho mínimo para o Princípio VI da constitution (Observabilidade como Requisito de Primeira Classe — Padrão OpenTelemetry + SigNoz), que exige que todo serviço seja instrumentado desde o início cobrindo os três pilares (logs estruturados, métricas, traces distribuídos). Este item NÃO inclui a stack completa de observabilidade da Fase 6 (sem OTel Collector rodando, sem exportar para o SigNoz ainda, sem dashboards) — é só a instrumentação básica do lado da aplicação. Escopo: (1) traces distribuídos reais via OpenTelemetry para toda requisição HTTP; (2) logs estruturados (JSON) nos pontos de transição de estado de cada Service, incluindo trace_id/span_id da requisição corrente; (3) métricas básicas expostas via o endpoint /actuator já existente. Fora de escopo: OTel Collector, exportação para SigNoz, dashboards, sampling/retenção de produção — tudo isso é Fase 6. Contrato HTTP não muda. Último item da Fase 1."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Toda requisição gera um trace distribuído rastreável (Priority: P1)

Como desenvolvedor do laboratório, quero que toda requisição HTTP recebida por qualquer um
dos 3 serviços gere um trace distribuído com um identificador único, para que eu consiga, no
futuro (quando um backend de observabilidade real existir), seguir o caminho completo de uma
requisição através do sistema — e, desde já, correlacionar essa requisição aos logs que ela
gerou.

**Why this priority**: é o pilar central de observabilidade que este item introduz — sem
traces reais, não há como uma futura investigação de incidente (Fase 7, SRE Autônomo) sequer
começar a reconstruir "o que aconteceu" numa requisição específica.

**Independent Test**: fazer uma requisição HTTP a qualquer um dos 3 serviços e confirmar, nos
logs gerados, a presença de um identificador de trace único associado a essa requisição.

**Acceptance Scenarios**:

1. **Given** os 3 serviços rodando, **When** uma requisição HTTP é feita a qualquer endpoint,
   **Then** um trace distribuído é gerado com um identificador único para essa requisição.
2. **Given** nenhum backend de observabilidade (Collector/SigNoz) configurado ou disponível,
   **When** os serviços recebem requisições normalmente, **Then** eles continuam respondendo
   sem erro, degradação de performance perceptível ou falha de inicialização — a
   instrumentação nunca é um requisito para o serviço funcionar.
3. **Given** os mesmos cenários de sucesso e erro já cobertos pelos itens 1.3-1.11
   (criação, busca, 404, validação, transições, persistência, testes self-contained),
   **When** exercitados com a instrumentação ativa, **Then** o comportamento observável pela
   API MUST permanecer idêntico ao anterior — esta feature adiciona visibilidade, não muda o
   que a API faz.

---

### User Story 2 - Logs de transição de estado são estruturados e correlacionáveis ao trace (Priority: P2)

Como desenvolvedor do laboratório, quero que os logs emitidos nos pontos de transição de
estado de cada recurso (criar, confirmar/emitir, cancelar) estejam em formato estruturado e
incluam o identificador do trace da requisição que os originou, para que — mesmo sem nenhum
backend de observabilidade rodando ainda — eu já consiga correlacionar manualmente "este log
pertence a esta requisição" só olhando os logs brutos.

**Why this priority**: depende de US1 já existir (não há trace_id pra incluir no log sem um
trace primeiro), e o valor de logs estruturados só se realiza plenamente quando um backend
real (Fase 6) puder indexá-los — mas correlação manual via trace_id já é útil desde já.

**Independent Test**: disparar uma transição de estado (ex.: confirmar um pedido) e confirmar
que o log gerado está em formato estruturado e contém o mesmo identificador de trace da
requisição HTTP que a originou.

**Acceptance Scenarios**:

1. **Given** uma requisição que cria, confirma/emite ou cancela um recurso em qualquer um dos
   3 serviços, **When** a transição de estado ocorre, **Then** um log estruturado (não texto
   plano) é emitido, contendo o identificador do trace e do span da requisição corrente.
2. **Given** dois logs de transições de estado de requisições diferentes, **When**
   comparados, **Then** cada um carrega o identificador de trace da sua própria requisição —
   nunca um identificador de outra requisição ou um valor vazio/ausente.

---

### Edge Cases

- **OTel Collector/backend indisponível ou não configurado** (situação normal neste item,
  já que a stack completa só chega na Fase 6): a instrumentação MUST degradar graciosamente
  — sem travar o startup, sem lançar exceção nas requisições, no máximo registrando uma
  tentativa de exportação falha em segundo plano.
- **Requisição que dispara chamadas internas adicionais** (ex.: acesso ao banco via JPA):
  essas chamadas MUST aparecer como parte do mesmo trace da requisição HTTP que as originou,
  não como traces desconexos — permite reconstruir a jornada completa de uma requisição.
- **Métricas expostas via `/actuator`**: MUST se limitar a dados operacionais básicos
  (contagem/latência de requisições, por exemplo) — nenhum dado de negócio sensível
  (ex.: valores monetários, identificadores de cliente) deve vazar por essa via.
- **Os 3 serviços usam o mesmo padrão de instrumentação**: nenhuma solução diferente por
  serviço, apesar de cada um continuar rodando de forma independente (Princípio I).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada requisição HTTP recebida pelos 3 serviços MUST gerar um trace distribuído
  com um identificador único.
- **FR-002**: Chamadas internas disparadas por uma requisição (ex.: acesso a banco) MUST
  aparecer associadas ao mesmo trace da requisição que as originou.
- **FR-003**: Os logs emitidos nos pontos de transição de estado de cada `Service`
  (criar/confirmar/cancelar em order-api e payment-api; criar/emitir/cancelar em invoice-api)
  MUST estar em formato estruturado (não texto plano).
- **FR-004**: Cada log de transição de estado MUST incluir o identificador do trace e do
  span da requisição corrente, permitindo correlação log↔trace sem depender de nenhum
  backend de observabilidade.
- **FR-005**: Os 3 serviços MUST expor métricas operacionais básicas via o endpoint
  `/actuator` já existente, sem introduzir um backend de métricas novo nesta etapa.
- **FR-006**: A ausência de um backend de observabilidade (Collector/SigNoz) configurado ou
  alcançável MUST NOT impedir o funcionamento normal de nenhum dos 3 serviços — nem no
  startup, nem ao responder requisições.
- **FR-007**: O contrato HTTP dos 3 serviços (endpoints, formatos de request/response,
  códigos de status já validados pelos itens 1.3-1.11) MUST permanecer idêntico.
- **FR-008**: Os 3 serviços MUST usar exatamente o mesmo mecanismo de instrumentação —
  implementado de forma independente por serviço (Princípio I), sem solução divergente.
- **FR-009**: Esta feature MUST deixar registrado explicitamente que a integração com um
  backend real de observabilidade (SigNoz), a criação de dashboards e qualquer configuração
  de sampling/retenção de produção são escopo da Fase 6 — não deste item.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma requisição HTTP a qualquer um dos 3 serviços produz um trace com
  identificador único, visível nos logs gerados por essa requisição.
- **SC-002**: Um log de transição de estado pode ser correlacionado à requisição HTTP que o
  originou usando apenas os logs brutos — sem depender de nenhum backend externo.
- **SC-003**: Os 3 serviços continuam iniciando e respondendo normalmente a requisições
  quando nenhum backend de observabilidade está disponível.
- **SC-004**: 100% dos cenários de sucesso e erro já cobertos pelos itens 1.3-1.11 (66 testes
  automatizados) continuam passando, sem qualquer mudança de comportamento observável pela
  API.

## Assumptions

- O mecanismo exato de instrumentação (agent Java vs. SDK/starter, artifacts envolvidos) MUST
  ser confirmado empiricamente durante o planejamento técnico (`/speckit-plan`), seguindo o
  padrão já estabelecido nesta sessão (itens 1.1, 1.10, 1.11) — não deve ser assumido a
  partir de conhecimento de versões anteriores do Spring Boot/ecossistema OTel.
- Nenhum OTel Collector é implantado nesta feature — os dados de trace/métrica são gerados
  pela aplicação, mas não precisam ter um destino funcional configurado ainda (isso é Fase
  6, já sequenciada no ROADMAP com SigNoz como backend, conforme a constitution).
- "Log estruturado" significa JSON (ou formato equivalente parseável por máquina), não
  necessariamente com um schema formal — o requisito é ser estruturado e incluir
  trace_id/span_id, não seguir um schema específico de terceiros nesta etapa.
- Métricas básicas via `/actuator` reaproveitam o `spring-boot-starter-actuator` já presente
  nos 3 serviços desde o item 1.1 — não introduz um novo endpoint/porta.
- Este é o último item da Fase 1 do ROADMAP — ao concluí-lo, a Fase 1 fica completa e o
  próximo passo é a Fase 2 (Kafka assíncrono).
