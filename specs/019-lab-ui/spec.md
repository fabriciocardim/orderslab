# Feature Specification: UI do Laboratório

**Feature Branch**: `019-lab-ui`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "U1 — UI do laboratório (frontend-react): interface gráfica para criar um pedido e acompanhar o fluxo order-api → payment-api → invoice-api, listando o que cada serviço guarda e dando acesso aos tópicos Kafka, sem backend novo. SPA servida atrás de um proxy que expõe as 3 APIs e o health de cada serviço na mesma origem (sem CORS e sem mudar nenhum serviço). Telas: novo pedido com cenários de valor e confirmar/cancelar; acompanhamento por pedido (pedido → pagamento → nota) derivado dos status; listagens; cartão de tópicos e DLTs com links para o Kafbat; saúde dos serviços. Erros e indisponibilidade explícitos. Implantação em Compose e k8s, CI próprio, sem autenticação e sem telemetria de navegador na v1. Fora de escopo: backend/BFF, leitura direta de Kafka ou banco, eventos ao vivo na tela, reprocessar/injetar mensagens, autenticação, mudanças nos serviços."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar um pedido e ver o fluxo acontecer (Priority: P1)

Como desenvolvedor do laboratório, quero criar um pedido por uma tela (ou por um botão de cenário pronto) e acompanhar,
na mesma tela, o pedido virando pagamento e depois nota, para entender como o fluxo assíncrono se comporta em cada
caminho sem precisar montar chamadas manuais nem abrir várias ferramentas.

**Why this priority**: é o objetivo central da UI — tornar o fluxo do laboratório visível e demonstrável. Sem isso, as
demais telas (listas, saúde, tópicos) são só consulta.

**Independent Test**: com os três serviços no ar, abrir a UI, escolher o cenário "valor baixo" e verificar que o pedido
aparece, que o pagamento e a nota aparecem em seguida e que cada etapa muda para "feito"; repetir com o cenário "acima
de 1000" e ver o pagamento falhar e a nota ficar "não se aplica".

**Acceptance Scenarios**:

1. **Given** a UI aberta e os serviços no ar, **When** o usuário preenche cliente e valor válidos e envia, **Then** o
   pedido é criado, passa a ser acompanhado e a etapa "pedido" fica "feito".
2. **Given** um pedido acompanhado com valor dentro dos limites (ex.: 10.50 ou exatamente 500), **When** o fluxo
   termina, **Then** as etapas pagamento e nota ficam "feito" (pagamento reservado e nota emitida).
3. **Given** um pedido com valor entre 500 e 1000, **When** o fluxo termina, **Then** o pagamento fica "feito" e a nota
   fica "falhou".
4. **Given** um pedido com valor acima de 1000, **When** o fluxo termina, **Then** o pagamento fica "falhou" e a nota
   fica "não se aplica" (nenhuma nota é emitida).
5. **Given** os botões de cenário (valor baixo, exatamente 500, entre 500 e 1000, exatamente 1000, acima de 1000),
   **When** o usuário clica em um, **Then** um pedido com um valor que exercita esse caminho é criado e passa a ser
   acompanhado, sem digitar nada.
6. **Given** um pedido acompanhado e ainda pendente, **When** o usuário confirma ou cancela o pedido pela tela, **Then**
   o estado do pedido muda de acordo e a tela reflete isso.
7. **Given** dados inválidos no formulário (cliente vazio, valor ausente, zero ou negativo), **When** o usuário tenta
   enviar, **Then** a UI aponta o campo com problema; e se o serviço recusar o pedido por validação, a mensagem de erro
   aparece no próprio formulário.

---

### User Story 2 - Acompanhar um pedido sem ficar na dúvida (Priority: P2)

Como desenvolvedor do laboratório, quero que o acompanhamento diga claramente em que etapa o pedido está — feito,
falhou, aguardando ou não se aplica — e que ele pare de "girar" quando não há mais o que esperar, para saber se algo
travou ou se só falta chegar.

**Why this priority**: refina a US1: a clareza dos estados é o que transforma "ver listas" em "entender o fluxo".

**Independent Test**: acompanhar um pedido recém-criado e observar a transição aguardando → feito; acompanhar um pedido
cujo fluxo não avança (ex.: um serviço parado) e ver a tela indicar que está aguardando e parar de atualizar depois de
um tempo.

**Acceptance Scenarios**:

1. **Given** um pedido acompanhado, **When** a etapa anterior está "feito" e o registro da etapa seguinte ainda não
   existe, **Then** essa etapa aparece como "aguardando".
2. **Given** um pedido acompanhado, **When** todas as etapas chegam a um estado final (feito, falhou ou não se aplica),
   **Then** a atualização automática para.
3. **Given** um pedido acompanhado que não muda por cerca de 30 segundos, **When** o tempo passa, **Then** a
   atualização automática para e a tela mostra que continua aguardando, sem indicador de carregamento infinito.
4. **Given** uma etapa que falhou, **When** o usuário olha o acompanhamento, **Then** vê que falhou e recebe o caminho
   (link) para consultar o evento correspondente na ferramenta de tópicos, já que o motivo detalhado não vem do serviço.
5. **Given** um pedido acompanhado, **When** a tela está aberta, **Then** a atualização automática acontece a cada
   cerca de 2 segundos e para quando o usuário deixa de observar aquele pedido.

---

### User Story 3 - Ver o que cada serviço guarda e se estão de pé (Priority: P3)

Como desenvolvedor do laboratório, quero ver listas de pedidos, pagamentos e notas como cada serviço as guarda, o estado
de saúde dos três serviços e um atalho para cada tópico Kafka e dead-letter topic, para ter uma visão geral do laboratório
e saltar para os eventos quando preciso.

**Why this priority**: visão geral e navegação; útil, mas depende do núcleo (US1/US2) para fazer sentido.

**Independent Test**: abrir as listagens e ver os registros dos três serviços; derrubar um serviço e ver apenas o painel
dele marcado como indisponível; clicar em um tópico e ser levado à página desse tópico na ferramenta de tópicos.

**Acceptance Scenarios**:

1. **Given** registros existentes, **When** o usuário abre as listagens, **Then** vê pedidos, pagamentos e notas com seu
   status, os 200 mais recentes de cada um.
2. **Given** a tela de saúde, **When** os serviços estão no ar, **Then** cada um aparece como "no ar"; **When** um deles
   para, **Then** só aquele aparece como "fora do ar" e os demais seguem normais.
3. **Given** o cartão de tópicos, **When** o usuário o abre, **Then** vê os sete tópicos de evento e os dois dead-letter
   topics, cada um com um link que abre a página correspondente na ferramenta de tópicos.
4. **Given** a URL base da ferramenta de tópicos, **When** ela é configurada na implantação, **Then** os links passam a
   usá-la sem recompilar a aplicação (padrão: o endereço local da ferramenta).
5. **Given** um serviço fora do ar, **When** o usuário usa a tela, **Then** o painel/lista daquele serviço mostra
   "indisponível" com o último dado conhecido (se houver) e o restante da tela continua funcionando.

---

### Edge Cases

- **Serviço fora do ar ou lento**: cada consulta tem limite de 3 segundos; estourar vira "indisponível" naquele painel,
  nunca uma tela travada ou em branco sem explicação.
- **Pedido inexistente / id inválido ao acompanhar**: mensagem clara de "não encontrado", sem erro técnico cru.
- **Listas grandes**: os serviços não paginam; a UI mostra só os 200 registros mais recentes e avisa que a lista foi
  limitada quando houver mais.
- **Pedido sem pagamento/nota ainda**: aparece como "aguardando" (não como falha) enquanto a etapa anterior está ok.
- **Valores nos limites**: exatamente 500 e exatamente 1000 são tratados como "dentro" (nota emitida / pagamento
  reservado), refletindo as regras dos serviços; os cenários prontos cobrem esses valores.
- **Confirmar/cancelar em estado inválido**: a recusa do serviço (conflito de estado) aparece como mensagem, sem quebrar a
  tela.
- **Ferramenta de tópicos indisponível**: os links continuam presentes (abrem a página da ferramenta, que pode estar
  fora do ar); a UI não depende dela para funcionar.
- **Várias abas/atualizações simultâneas**: cada aba atualiza por conta própria; nada exige estado compartilhado.
- **Mudança de contrato dos serviços**: a UI só usa as rotas existentes; se um serviço mudar de resposta, a UI mostra
  "indisponível/inesperado" naquele painel em vez de quebrar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A UI MUST permitir criar um pedido informando cliente e valor, validando os campos antes de enviar
  (cliente obrigatório; valor obrigatório e positivo) e exibindo no próprio formulário os erros de validação devolvidos
  pelo serviço de pedidos.
- **FR-002**: A UI MUST oferecer botões de cenário que criam, sem digitação, pedidos com valores que exercitam cada
  caminho do fluxo: valor baixo, exatamente 500, entre 500 e 1000, exatamente 1000 e acima de 1000.
- **FR-003**: A UI MUST permitir confirmar e cancelar um pedido pendente e refletir o resultado (inclusive a recusa por
  estado inválido) como mensagem.
- **FR-004**: A UI MUST exibir, para um pedido acompanhado, as três etapas pedido → pagamento → nota, cada uma em um
  estado: **feito**, **falhou**, **aguardando** ou **não se aplica**, derivado exclusivamente dos status devolvidos pelos
  serviços.
- **FR-005**: Regras de derivação: "aguardando" = etapa anterior concluída com sucesso e registro da etapa ainda
  inexistente; "não se aplica" = ausência de nota quando o pagamento falhou; "falhou" = status de falha do respectivo
  serviço; "feito" = status de sucesso do respectivo serviço.
- **FR-006**: A atualização do acompanhamento MUST ser automática a cada cerca de 2 segundos apenas enquanto houver pedido
  observado ou lista aberta, MUST parar ao chegar a um estado final e MUST parar após cerca de 30 segundos sem mudança,
  indicando "aguardando" sem animação de carregamento infinita.
- **FR-007**: Quando uma etapa falhar, a UI MUST oferecer o link para a ferramenta de tópicos para consultar o evento; o
  motivo detalhado da falha NÃO é exibido pela UI (os serviços não o expõem).
- **FR-008**: A UI MUST oferecer três listagens (pedidos, pagamentos, notas) com status, limitadas aos 200 registros
  mais recentes e avisando quando houver mais que isso.
- **FR-009**: A UI MUST exibir um cartão de tópicos com os sete tópicos de evento (`order.created`, `order.confirmed`,
  `order.cancelled`, `payment.reserved`, `payment.failed`, `invoice.issued`, `invoice.failed`) e os dois dead-letter
  topics (`order.created.dlt`, `payment.reserved.dlt`), cada um com um link para a página correspondente na ferramenta de
  tópicos do laboratório; a URL base dessa ferramenta MUST ser configurável na implantação (sem recompilar), com padrão
  `http://localhost:8090`.
- **FR-010**: A UI MUST exibir a saúde (no ar / fora do ar) dos três serviços de domínio.
- **FR-011**: Toda consulta a um serviço MUST ter limite de 3 segundos; falha ou estouro MUST marcar apenas o painel
  afetado como "indisponível" (mantendo o último dado conhecido, se houver) sem impedir o resto da tela, e nenhuma área
  MUST ficar em branco sem explicação.
- **FR-012**: A UI MUST falar apenas com a sua própria origem: as chamadas às três APIs e ao health de cada serviço são
  encaminhadas por um proxy configurável (um destino por serviço, definido na implantação), de modo que não exista
  dependência de CORS e **nenhum serviço de domínio precise ser alterado** (contratos HTTP intactos, nenhum código de
  `order-api`, `payment-api` ou `invoice-api` modificado).
- **FR-013**: A UI e seu proxy MUST ser empacotados e implantáveis no Docker Compose e no cluster Kubernetes local, no
  mesmo padrão dos demais componentes (Princípio IV), expondo a UI na porta `3000` do host no Compose.
- **FR-014**: A UI MUST ser um projeto independente (dependências, empacotamento e pipeline próprios) que se comunica com
  os serviços apenas por suas APIs REST (Princípio I), com pipeline de CI próprio (lint, testes, build, análise de
  segurança de JavaScript/TypeScript e empacotamento da imagem só depois dos demais passos) e versionamento por tag
  `frontend-react-vX.Y.Z`.
- **FR-015**: O proxy MUST registrar o acesso em log estruturado (JSON); a v1 MUST NOT incluir telemetria de navegador
  (decisão explícita dentro do Princípio VI) nem autenticação (a camada de segurança é de fase futura).
- **FR-016**: A suíte automatizada MUST cobrir: a derivação dos estados das etapas (todas as combinações de status dos
  três serviços), o cliente das APIs (sucesso, erro, timeout) com respostas simuladas e o componente de acompanhamento;
  MUST incluir verificação estática (lint e tipos) e o build, e MUST rodar sem exigir os serviços no ar.
- **FR-017**: Esta feature MUST NOT incluir backend próprio, leitura direta de Kafka ou de bancos pela UI, exibição de
  eventos ao vivo dentro da tela, reprocessamento ou injeção de mensagens, autenticação, telemetria de navegador,
  filtros ou paginação nos serviços, nem qualquer alteração nos serviços de domínio.

### Key Entities *(include if feature involves data)*

- **Pedido, Pagamento, Nota**: registros já existentes em cada serviço (identificador, pedido de origem, valor, status,
  datas). A UI só os **lê** (e cria/confirma/cancela pedidos pela API existente); não guarda nada.
- **Etapa do acompanhamento**: uma das três posições do fluxo (pedido, pagamento, nota) com um estado derivado
  (feito, falhou, aguardando, não se aplica); existe só na tela.
- **Cenário**: valor pré-definido associado a um caminho do fluxo (baixo, 500, entre 500 e 1000, 1000, acima de 1000).
- **Tópico / Dead-letter topic**: nome de um tópico do laboratório, apresentado com um link para a ferramenta de tópicos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário consegue criar um pedido por um botão de cenário e ver as três etapas chegarem a um estado
  final correto em até 15 segundos, sem abrir nenhuma outra ferramenta.
- **SC-002**: Para cada um dos cinco cenários de valor, o estado final exibido das três etapas corresponde ao esperado
  (100% dos cenários).
- **SC-003**: Com um serviço fora do ar, 100% das demais áreas da tela continuam funcionando e só a área daquele
  serviço aparece como indisponível; nenhuma tela fica em branco sem explicação.
- **SC-004**: A atualização automática nunca continua indefinidamente: para em até 30 segundos sem mudança ou ao
  chegar a um estado final (verificável em 100% dos acompanhamentos).
- **SC-005**: 100% dos nove tópicos (sete de evento e dois DLTs) têm link que abre a página correta na ferramenta de
  tópicos, e a URL base é trocável sem recompilar.
- **SC-006**: Zero alterações nos serviços de domínio: nenhum arquivo de `order-api`, `payment-api` ou `invoice-api`
  muda e suas suítes continuam verdes.
- **SC-007**: A suíte automatizada da UI roda em menos de 2 minutos sem depender dos serviços e cobre todas as
  combinações de estado das etapas.
- **SC-008**: A UI sobe pelo Docker Compose e pelo Kubernetes local no mesmo padrão dos demais componentes e abre na
  porta `3000` (Compose).

## Assumptions

- Esta feature é o item U1 do ROADMAP (complemento fora da numeração das fases, adiantamento declarado). As decisões vêm
  do brainstorming de 2026-10-05: UI fina sem backend novo; BFF/console de eventos descartado (duplicaria o Kafbat e o
  tracing da Fase 6 e pesa numa máquina de 8 GB).
- O Kafbat UI já existe no laboratório (Compose e k8s) e é a ferramenta de tópicos usada pelos links; esta feature não a
  altera. O formato exato das URLs de página de tópico do Kafbat é confirmado no `/speckit-plan`.
- Os serviços já expõem `GET` de lista e por id, `POST` de criação, confirmação e cancelamento, e o endpoint de saúde,
  nos caminhos atuais; a UI só os usa. As listas não têm filtro por pedido nem paginação (limitação aceita, evolução
  separada).
- Os limites de decisão (1000.00 para pagamento, 500.00 para nota) são os padrões atuais dos serviços; os cenários da UI
  assumem esses padrões e a documentação dos cenários os cita.
- O "pedido pendente" só muda de estado por confirmação/cancelamento manual (REST), já que a cadeia assíncrona não
  confirma o pedido sozinha.
- Sem autenticação e sem telemetria de navegador na v1; a UI é uma ferramenta local do laboratório. Quando a Fase 5
  chegar, ela é o frontend a integrar (item E5.5).
- O ambiente de desenvolvimento tem Node.js e npm; a máquina tem 8 GB de RAM, então a validação empírica evita rodar
  todos os serviços em contêineres ao mesmo tempo.
