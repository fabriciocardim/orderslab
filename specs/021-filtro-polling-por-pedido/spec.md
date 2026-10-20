# Feature Specification: Filtro por Pedido no Polling e Listas Limitadas

**Feature Branch**: `021-filtro-polling-por-pedido` (implementado na branch `feature/T001_teste_carga`)

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "T2 — corrigir o gargalo achado no teste de carga (T1): a tela de acompanhamento e a aba Listagens da UI baixam a cada 2 s as listas COMPLETAS de pagamentos e notas (`findAll()` sem filtro nem paginação) para achar um único pedido. No perfil `load` (10 pedidos/s, 50 visitantes) as listas passaram de 800 KB, as respostas passaram de 3 s, o nginx devolveu 8.646 504, 6 GB foram baixados e a VPS travou por falta de memória. Permitir buscar pagamentos e notas de um pedido específico, limitar as listas aos registros mais recentes com o total informado à parte, indexar as colunas consultadas, e fazer a UI e o teste de carga usarem isso."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Acompanhar um pedido custa o mesmo com 10 ou 100 mil pedidos no banco (Priority: P1)

Como usuário da UI acompanhando um pedido, quero que cada atualização da tela consulte só o pagamento e a nota do meu
pedido, para que o acompanhamento continue rápido mesmo quando o sistema tiver acumulado muitos pedidos (ex.: Black
Friday).

**Why this priority**: é a causa direta do colapso medido no teste de carga — o custo de cada atualização crescia com o
total de pedidos e era pago por todos que estavam com a tela aberta.

**Independent Test**: com milhares de pagamentos e notas no banco, criar um pedido e acompanhar pela UI; conferir que
cada rodada de atualização transfere poucos bytes (independente do total) e que o fluxo chega ao estado final igual a
hoje.

**Acceptance Scenarios**:

1. **Given** um pedido cujo pagamento já existe, **When** o pagamento é consultado pelo id do pedido, **Then** a
   resposta traz só os pagamentos desse pedido (normalmente um), do mais recente para o mais antigo.
2. **Given** um pedido cujo pagamento ainda não foi criado, **When** o pagamento é consultado pelo id do pedido,
   **Then** a resposta é uma lista vazia com sucesso (não é erro: o pagamento ainda está a caminho).
3. **Given** o mesmo para notas, **When** a nota é consultada pelo id do pedido, **Then** valem as mesmas regras.
4. **Given** um id de pedido em formato inválido, **When** a consulta é feita, **Then** o serviço recusa com erro de
   validação no formato de erro padrão do projeto.
5. **Given** a tela de acompanhamento aberta, **When** ela atualiza, **Then** consulta o pedido, o pagamento e a nota
   **daquele pedido** — nunca as listas completas — e os estados exibidos (feito/falhou/aguardando/não se aplica) e a
   parada automática se comportam exatamente como antes.

---

### User Story 2 - Listagens não crescem sem limite (Priority: P2)

Como usuário da aba Listagens, quero ver os registros mais recentes de cada serviço e saber quantos existem no total,
sem que a tela baixe tudo que o banco guarda a cada atualização.

**Why this priority**: a aba Listagens tem o mesmo problema da US1 (3 listas completas a cada 2 s), mas é menos usada
que o acompanhamento.

**Independent Test**: com mais de 200 registros em um serviço, abrir a aba Listagens e conferir que a lista mostra os
200 mais recentes, que o aviso de lista limitada mostra o total real e que cada atualização transfere um volume fixo.

**Acceptance Scenarios**:

1. **Given** mais registros do que o limite, **When** a lista é pedida sem filtro, **Then** a resposta traz no máximo o
   limite (padrão 200), do mais recente para o mais antigo, e informa o total à parte.
2. **Given** um limite pedido entre 1 e 500, **When** a lista é pedida, **Then** esse limite é respeitado; **Given** um
   limite fora dessa faixa ou não numérico, **Then** o serviço recusa com erro de validação no formato padrão.
3. **Given** a aba Listagens, **When** o total passa do limite, **Then** o aviso "Lista limitada aos 200 registros mais
   recentes (de N)" mostra o total real informado pelo serviço.

---

### User Story 3 - O teste de carga mede o sistema corrigido (Priority: P3)

Como desenvolvedor, quero que a suíte de carga (T1) simule a UI corrigida, para comparar a nova rodada com a que
derrubou o sistema.

**Why this priority**: valida a correção, mas depende das US1 e US2.

**Independent Test**: rodar o perfil `smoke` e conferir que o acompanhamento usa as consultas por pedido e que o tamanho
das respostas de pagamento/nota fica estável ao longo do teste.

**Acceptance Scenarios**:

1. **Given** a suíte de carga, **When** um comprador acompanha o pedido, **Then** usa as consultas por pedido e o tamanho
   medido das respostas não cresce com o número de pedidos.
2. **Given** um visitante na aba Listagens, **When** atualiza, **Then** pede as listas limitadas.

### Edge Cases

- Pedido com mais de um pagamento ou nota (ex.: criado manualmente pelo `POST` direto): todos voltam, mais recente
  primeiro; a UI continua escolhendo o mais recente.
- Id de pedido em maiúsculas: a consulta encontra o registro (os ids são guardados em minúsculas).
- Filtro por pedido e limite enviados juntos: vale o filtro por pedido (um limite válido é ignorado; um inválido é
  recusado como em qualquer outra chamada).
- Banco vazio: lista vazia e total 0.
- Front antigo contra serviço novo (durante o deploy): a lista sem filtro passa a trazer só os 200 mais recentes; o
  pedido acompanhado foi criado agora, então continua sendo encontrado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: payment-api e invoice-api MUST permitir consultar os registros de um pedido pelo id do pedido, devolvendo
  uma lista (vazia quando ainda não há registro), do mais recente para o mais antigo.
- **FR-002**: payment-api, invoice-api e order-api MUST, na listagem sem filtro, devolver no máximo N registros mais
  recentes (padrão 200, aceito de 1 a 500) e informar o total de registros separadamente do corpo.
- **FR-003**: Os serviços MUST validar o id do pedido (UUID) e o limite, recusando valores inválidos com o formato de
  erro padrão do projeto (HTTP 400 com a lista de erros de validação).
- **FR-004**: As consultas por pedido e por ordem de criação MUST usar índices no banco de cada serviço, para que o custo
  não cresça com o tamanho da tabela.
- **FR-005**: A tela de acompanhamento MUST consultar pagamento e nota só do pedido acompanhado, mantendo inalterados os
  estados exibidos, a degradação por painel e a parada automática.
- **FR-006**: A aba Listagens MUST pedir as listas limitadas e mostrar o total real informado pelo serviço no aviso de
  lista limitada.
- **FR-007**: O formato de cada registro devolvido MUST continuar o mesmo de hoje (nenhum campo removido ou renomeado).
- **FR-008**: A suíte de carga MUST usar as novas consultas para simular a UI corrigida.
- **FR-009**: Toda mudança MUST ter testes automatizados (controller, service e persistência nos serviços; componentes e
  hooks no frontend) e a suíte atual MUST continuar passando.

### Key Entities

- **Pagamento** e **Nota**: registros já existentes, ligados a um pedido pelo id do pedido; nada muda na estrutura.
- **Total da listagem**: quantidade de registros existentes no serviço, informada junto da lista limitada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cada atualização da tela de acompanhamento transfere menos de 2 KB no total, independente do número de
  pedidos no banco (hoje: ~1,6 MB com ~3,6 mil pedidos).
- **SC-002**: Cada atualização da aba Listagens transfere um volume limitado a 200 registros por serviço, independente do
  total.
- **SC-003**: Uma consulta por pedido responde em menos de 100 ms (p95) com 10 mil registros na tabela, em condições
  normais.
- **SC-004**: Nenhuma regressão: todos os testes atuais dos 3 serviços e do frontend continuam passando.
- **SC-005**: Repetido o perfil `load` de T1 após o deploy, nenhuma resposta de pagamento/nota passa de 3 s por causa do
  tamanho das listas (verificação pós-deploy, fora da validação automatizada desta feature).

## Assumptions

- Os únicos consumidores das listagens são a UI do laboratório e a suíte de carga; por isso a listagem sem filtro pode
  passar a ser limitada por padrão (mudança de comportamento declarada).
- O limite padrão de 200 é o mesmo que a UI já usa para exibir (`LIST_LIMIT`).
- Paginação completa (página 2, 3…) está fora de escopo: a UI só mostra os mais recentes.
- Os limites de memória dos containers e o restante da contenção de recursos achada no teste de carga são outra feature
  (fora de escopo aqui).
- Não há mudança de infraestrutura: o proxy da UI já repassa query string e cabeçalhos.
- **Adiantamento declarado** (Governance da constitution): esta feature não estava no `ROADMAP.md`; nasce do teste de
  carga T1 e entra como item T2 do complemento "Desempenho sob carga", fora da numeração das fases (como a U1).
