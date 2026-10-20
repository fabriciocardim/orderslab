# Feature Specification: Limites de Memória da Stack

**Feature Branch**: `022-limites-memoria-stack` (implementado na branch `feature/T001_teste_carga`)

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "T3 — conter o estrago quando algo der errado sob carga. No teste de carga T1 (perfil `load`), a VPS de 2 vCPU / 7,75 GB sem swap esgotou a RAM (7,60/7,75 GB, load average 310, thrashing) e travou inteira, levando junto o Traefik e o SSH; foi preciso reiniciar a máquina. Nenhum container tinha limite de memória, as JVMs não tinham teto de heap, o Jaeger guardava todos os traces em memória com amostragem de 100%, todo SQL era logado e os logs dos containers não tinham rotação. Limitar a memória de cada componente, fazer o heap das JVMs seguir esse limite, limitar o que o backend de traces guarda, tornar amostragem de traces e log de SQL configuráveis por ambiente, rotacionar logs e reiniciar automaticamente o que cair."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Um componente sem memória não derruba a máquina (Priority: P1)

Como responsável pela VPS, quero que, se um componente da stack consumir memória demais, só ele seja interrompido e
reiniciado, sem travar o servidor inteiro, o proxy de borda e o acesso remoto.

**Why this priority**: foi o que transformou um gargalo da aplicação numa queda total que só um reboot resolveu.

**Independent Test**: com a stack de pé, forçar um componente a passar do próprio limite e confirmar que ele é
encerrado e volta sozinho, enquanto os demais continuam respondendo e a máquina segue acessível.

**Acceptance Scenarios**:

1. **Given** a stack de pé, **When** um componente passa do limite de memória dele, **Then** só esse componente é
   encerrado e volta a rodar sozinho, e os demais seguem respondendo.
2. **Given** a soma dos limites de todos os componentes, **When** comparada à memória da máquina, **Then** sobra
   margem para o sistema operacional e o proxy de borda (~1 GB numa máquina de 8 GB).
3. **Given** um serviço Java com limite de memória, **When** ele sobe, **Then** o heap máximo da JVM fica abaixo desse
   limite, deixando espaço para a memória fora do heap.

---

### User Story 2 - A telemetria não cresce sem limite (Priority: P2)

Como operador, quero que traces, métricas e logs ocupem um espaço limitado, para que a observabilidade não seja ela
mesma a causa da falta de memória ou de disco durante um pico.

**Why this priority**: o backend de traces era um dos maiores consumidores de memória e crescia a cada requisição.

**Independent Test**: gerar muitas requisições e confirmar que o backend de traces para de crescer depois do teto
configurado e que os logs dos containers são rotacionados.

**Acceptance Scenarios**:

1. **Given** o backend de traces em memória, **When** recebe mais traces que o teto, **Then** descarta os mais antigos
   em vez de crescer.
2. **Given** um ambiente de produção, **When** a amostragem de traces é configurada abaixo de 100% por variável de
   ambiente, **Then** os serviços passam a amostrar essa fração, sem recompilar nada.
3. **Given** o log de SQL, **When** o ambiente não pede o contrário, **Then** os serviços não logam cada comando SQL.
4. **Given** os logs dos containers, **When** passam do tamanho configurado, **Then** são rotacionados.
5. **Given** o Collector de telemetria sob pressão de memória, **When** passa do próprio teto, **Then** recusa dados
   novos temporariamente em vez de crescer até ser morto.

### Edge Cases

- Limite pequeno demais para o uso normal: o componente reiniciaria em laço. Os limites partem do uso observado mais
  margem e ficam documentados para ajuste.
- Ambiente de desenvolvimento local: a amostragem continua em 100% por padrão (só produção reduz por variável).
- Kubernetes local com 3 réplicas de cada API: requests/limits por pod; a soma é documentada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Todo componente da stack (Compose e Kubernetes) MUST ter um limite de memória declarado.
- **FR-002**: A soma dos limites no Compose MUST deixar pelo menos ~1 GB livre numa máquina de 8 GB.
- **FR-003**: Os processos Java (3 serviços, Kafbat) MUST dimensionar o heap a partir do limite do container; o Kafka
  MUST ter heap fixo compatível com o seu limite.
- **FR-004**: O backend de traces MUST ter um teto de traces em memória.
- **FR-005**: A amostragem de traces e o log de SQL dos serviços MUST ser configuráveis por variável de ambiente no
  Compose e no Kubernetes, com padrão de amostragem 100% e log de SQL desligado.
- **FR-006**: O Collector MUST limitar a própria memória (recusando dados acima do teto).
- **FR-007**: Os logs dos containers no Compose MUST ser rotacionados por tamanho.
- **FR-008**: Componentes encerrados por falta de memória MUST reiniciar sozinhos.
- **FR-009**: O orçamento de memória e a recomendação de swap no servidor MUST estar documentados.
- **FR-010**: Nenhum comportamento dos serviços MUST mudar além do log de SQL (testes atuais continuam passando).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Soma dos limites de memória no Compose ≤ 6,5 GB.
- **SC-002**: Repetido o perfil `load` de T1 após o deploy, a máquina continua acessível durante todo o teste, mesmo
  que algum componente seja reiniciado (verificação pós-deploy).
- **SC-003**: A configuração do Compose e dos manifests é válida (`docker compose config`, parse dos YAML).
- **SC-004**: Nenhuma regressão nos testes dos 3 serviços.

## Assumptions

- O servidor de produção é a VPS de 2 vCPU / 7,75 GB que roda `infra/docker-compose.yml` com Traefik na frente.
- Criar swap no servidor é configuração do host, fora do repositório; aqui fica só a recomendação documentada.
- Os limites iniciais são estimativas a partir do uso observado (htop do incidente) e devem ser recalibrados com o
  `load-tests/scripts/monitor.sh` (`containers.csv`) no próximo teste.
- **Adiantamento declarado**: item T3 do complemento "Desempenho sob carga", fora da ordem das fases (Fase 4 trata de
  hardening de k8s, mas não deste incidente).
