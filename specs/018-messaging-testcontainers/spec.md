# Feature Specification: Testes de Mensageria com Kafka Real (Testcontainers)

**Feature Branch**: `018-messaging-testcontainers`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "E2.6 — Testes de mensageria com Testcontainers Kafka por serviço (order-api, payment-api, invoice-api), cada um com a sua suíte (Princípio I). Hoje os testes mockam o KafkaTemplate e desligam o listener; o Kafka real só foi validado manualmente nos itens E2.2–E2.5. Transformar essa validação manual em testes automatizados e repetíveis contra um Kafka real subido por Testcontainers (junto com o Postgres real), dentro de ./mvnw test (só runtime de contêiner). order-api: eventos nos tópicos certos, chave = orderId, sem __TypeId__, envelope completo, tópicos com 3 partições, broker indisponível não afeta o HTTP e os eventos chegam depois de religar. payment-api: OrderCreated real → pagamento decidido + PaymentReserved/PaymentFailed, reentrega não duplica, mensagem inválida vai ao DLT com chave/valor idênticos e headers, mensagens seguintes seguem. invoice-api: idem com PaymentReserved → InvoiceIssued/InvoiceFailed e payment.reserved.dlt. As suítes existentes permanecem. Testes rápidos e estáveis. Única mudança em pom: dependência de teste do módulo Kafka do Testcontainers. Fora de escopo: ponta a ponta atravessando os três serviços, propagação de trace por headers (E6.4), carga/desempenho, mudanças de comportamento de produção."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - O order-api publica os eventos corretos num Kafka real, mesmo com o broker indisponível por um tempo (Priority: P1)

Como desenvolvedor do laboratório, quero que a suíte automatizada do `order-api` suba um Kafka real,
execute criar/confirmar/cancelar pedido pela API e confirme, consumindo os tópicos de verdade, que cada
operação gera exatamente o evento certo no tópico certo — e que, se o broker ficar indisponível, a API
continua respondendo e os eventos chegam depois que o broker volta — para que essa garantia deixe de
depender de uma validação manual e passe a ser protegida contra regressões.

**Why this priority**: o `order-api` é a origem de toda a cadeia; é onde a garantia de entrega
(outbox, ordem, sem perda) tem mais valor e onde o teste com Kafka mockado deixa o maior buraco.

**Independent Test**: rodar apenas a suíte de mensageria do `order-api` e vê-la passar com um Kafka e um
Postgres reais subidos automaticamente, sem nenhuma infraestrutura previamente provisionada.

**Acceptance Scenarios**:

1. **Given** um Kafka real, **When** um pedido é criado, confirmado e outro é cancelado pela API,
   **Then** os tópicos `order.created`, `order.confirmed` e `order.cancelled` recebem exatamente um
   evento por operação, com chave igual ao `orderId`, sem header de tipo Java, e payload com o envelope
   completo do contrato do E2.1 (`eventId`, `eventType`, `eventVersion` = 1, `occurredAt`, `orderId`).
2. **Given** o serviço recém-iniciado, **When** os tópicos são inspecionados, **Then** os três têm 3
   partições.
3. **Given** vários pedidos criados e confirmados em sequência, **When** os eventos de cada pedido são
   consumidos, **Then** o evento de criação de cada pedido precede o de confirmação/cancelamento.
4. **Given** o broker indisponível, **When** pedidos são criados e confirmados pela API, **Then** as
   respostas HTTP são as mesmas de sempre, os eventos permanecem pendentes e, **quando o broker volta**,
   todos são entregues no tópico certo, sem perda e sem duplicata.

---

### User Story 2 - O payment-api consome e publica de verdade num Kafka real (Priority: P2)

Como desenvolvedor do laboratório, quero que a suíte do `payment-api` publique `OrderCreated` num Kafka
real e verifique que o pagamento é decidido e o resultado sai no tópico certo, que reentregas não
duplicam, e que mensagens inválidas vão ao dead-letter topic com tudo preservado — sem que as mensagens
seguintes sejam afetadas.

**Why this priority**: valida de ponta a ponta, no serviço, o par consumidor+produtor e o DLT que hoje só
foram vistos manualmente. Depende de nada de US1, mas o padrão de teste nasce nela.

**Independent Test**: rodar apenas a suíte de mensageria do `payment-api` e vê-la passar contra Kafka e
Postgres reais.

**Acceptance Scenarios**:

1. **Given** um `OrderCreated` com valor dentro do limite publicado no Kafka real, **When** o serviço o
   consome, **Then** um pagamento reservado é persistido e um `PaymentReserved` aparece em
   `payment.reserved` com chave = `orderId`; com valor acima do limite, um `PaymentFailed` aparece em
   `payment.failed`.
2. **Given** o mesmo `OrderCreated` (mesmo `eventId`) publicado várias vezes, **When** o serviço os
   consome, **Then** existe um único pagamento e um único evento de saída para o pedido.
3. **Given** mensagens inválidas (ilegível, campo obrigatório ausente, valor não positivo) publicadas em
   `order.created`, **When** o serviço as consome, **Then** cada uma aparece em `order.created.dlt` com
   chave e valor idênticos aos originais e headers de diagnóstico, sem retries, e uma mensagem válida
   publicada depois é processada normalmente.
4. **Given** os tópicos de saída, **When** inspecionados, **Then** `payment.reserved`, `payment.failed` e o
   DLT têm 3 partições e os eventos não têm header de tipo Java.

---

### User Story 3 - O invoice-api consome e publica de verdade num Kafka real (Priority: P3)

Como desenvolvedor do laboratório, quero o mesmo nível de proteção para o `invoice-api`: `PaymentReserved`
real vira nota decidida e `InvoiceIssued`/`InvoiceFailed` no tópico certo, reentrega não duplica e
mensagem inválida vai a `payment.reserved.dlt`.

**Why this priority**: é o espelho de US2 para o último elo da cadeia; entrega o mesmo valor, mas reaproveita
o padrão já estabelecido.

**Independent Test**: rodar apenas a suíte de mensageria do `invoice-api` e vê-la passar contra Kafka e
Postgres reais.

**Acceptance Scenarios**:

1. **Given** um `PaymentReserved` com valor dentro do limite de emissão, **When** consumido, **Then** uma
   nota emitida é persistida e um `InvoiceIssued` aparece em `invoice.issued`; acima do limite, um
   `InvoiceFailed` aparece em `invoice.failed`.
2. **Given** o mesmo `PaymentReserved` publicado várias vezes, **Then** há uma única nota e um único
   evento de saída.
3. **Given** mensagens inválidas em `payment.reserved`, **Then** vão a `payment.reserved.dlt` com chave/valor
   idênticos e headers de diagnóstico, e uma válida publicada depois é processada.
4. **Given** os tópicos, **Then** `invoice.issued`, `invoice.failed` e o DLT têm 3 partições.

---

### Edge Cases

- **Execução repetida**: os testes MUST passar de forma repetível, em qualquer ordem, sem depender de
  estado deixado por execuções anteriores (cada teste usa identificadores próprios e filtra por eles).
- **Tempo de espera**: a verificação de que um evento "chegou" MUST usar espera com limite de tempo e
  verificação periódica, nunca pausas fixas; falha por timeout MUST dizer o que se esperava.
- **Broker pausado**: ao pausar e retomar o broker, o teste MUST aguardar a recuperação do serviço sem
  depender de tempos mágicos, e MUST sempre retomar o broker ao final, mesmo se o teste falhar, para não
  contaminar os testes seguintes.
- **Retry nos testes**: a política de retry (esperas) deve poder ser encurtada por configuração de teste para
  o DLT por falha transitória não deixar a suíte lenta; o comportamento de produção não muda.
- **Ambiente sem contêiner**: sem runtime de contêiner disponível, a suíte falha de forma clara (como já
  ocorre com os testes de banco desde o item 1.11); nenhuma outra infraestrutura é exigida.
- **Suítes existentes**: continuam passando sem alteração; os novos testes não as substituem.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada serviço (`order-api`, `payment-api`, `invoice-api`) MUST ter uma suíte de mensageria
  automatizada que roda contra um broker Kafka real e um Postgres real, ambos subidos e derrubados
  automaticamente pela própria suíte, dentro de `./mvnw test`.
- **FR-002**: A suíte do `order-api` MUST verificar, consumindo os tópicos reais, que criar, confirmar e
  cancelar pedido gera um evento por operação no tópico correto, com chave = `orderId`, sem header de tipo
  Java e envelope completo do contrato do E2.1; MUST verificar a ordem criação→confirmação/cancelamento por
  pedido e que os tópicos têm 3 partições.
- **FR-003**: A suíte do `order-api` MUST verificar que, com o broker indisponível, a API responde como de
  costume e os eventos pendentes são entregues, sem perda nem duplicata, depois que o broker volta; o
  broker MUST ser retomado ao final do teste em qualquer resultado.
- **FR-004**: A suíte do `payment-api` MUST verificar, com mensagens publicadas no Kafka real, que um pedido
  dentro do limite gera pagamento reservado + `PaymentReserved` e um acima do limite gera `PaymentFailed`,
  com chave = `orderId`, e que reentregas do mesmo `eventId` não duplicam pagamento nem evento.
- **FR-005**: A suíte do `invoice-api` MUST verificar o equivalente com `PaymentReserved` →
  `InvoiceIssued`/`InvoiceFailed`, incluindo a não-duplicação em reentregas.
- **FR-006**: As suítes de `payment-api` e `invoice-api` MUST verificar que mensagens inválidas (ilegível,
  campo obrigatório ausente, valor não positivo) chegam ao DLT do respectivo tópico de origem com chave e
  valor idênticos aos originais e com os headers de diagnóstico, sem retries, e que uma mensagem válida
  publicada depois é processada normalmente.
- **FR-007**: As suítes MUST verificar que os tópicos de saída e os DLTs de cada serviço têm 3 partições e
  que nenhum evento publicado carrega header de tipo Java.
- **FR-008**: Os testes MUST ser isolados e repetíveis: cada teste usa identificadores próprios e filtra os
  resultados por eles; MUST passar em qualquer ordem e em execuções consecutivas sem limpeza manual.
- **FR-009**: A espera por eventos MUST usar verificação periódica com limite de tempo e mensagem de falha
  descritiva; é proibido usar pausas fixas como mecanismo de sincronização.
- **FR-010**: Os parâmetros de teste (ex.: esperas de retry e intervalo do relay) MUST poder ser ajustados por
  configuração de teste para manter a suíte rápida e estável, sem alterar o comportamento de produção.
- **FR-011**: As suítes existentes (unidade e integração com broker simulado) MUST permanecer e continuar
  passando; os novos testes são adicionais.
- **FR-012**: Cada serviço MUST manter a sua suíte independente: nenhum código de teste compartilhado entre
  serviços (Princípio I). A única mudança permitida em `pom.xml` de cada serviço é a dependência de teste do
  módulo Kafka do Testcontainers (escopo test).
- **FR-013**: Esta feature MUST NOT alterar o comportamento de produção (nenhuma mudança em código de
  `src/main` além, se estritamente necessário, de configuração de teste em `src/test`), MUST NOT incluir
  teste ponta a ponta atravessando os três serviços, propagação de trace por headers Kafka (E6.4) nem testes de
  carga.
- **FR-014**: `./mvnw test` (e `verify`) de cada serviço MUST continuar autossuficiente: exige apenas um
  runtime de contêiner; MUST seguir passando o PMD e o restante do pipeline.

### Key Entities *(include if feature involves data)*

- **Suíte de mensageria**: conjunto de testes de um serviço que exercita publicação e/ou consumo contra um
  broker real; é independente da suíte de cada outro serviço.
- **Ambiente de teste**: broker Kafka e banco Postgres reais, efêmeros, subidos e derrubados pela própria
  suíte; compartilhados entre os testes de mensageria de um mesmo serviço para manter a execução rápida.
- **Mensagem de teste**: evento ou texto publicado no broker pelo teste, com identificadores únicos, para
  verificar o efeito no serviço sem interferir em outros testes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: As garantias validadas manualmente nos itens E2.2–E2.5 passam a ser verificadas
  automaticamente: 100% dos cenários listados em FR-002 a FR-007 têm pelo menos um teste automatizado contra
  Kafka real em cada serviço aplicável.
- **SC-002**: Rodar `./mvnw verify` em cada serviço, só com um runtime de contêiner disponível, termina com
  sucesso e inclui a suíte de mensageria.
- **SC-003**: A suíte de mensageria de cada serviço passa em 10 execuções consecutivas, sem falhas
  intermitentes e sem limpeza manual entre elas.
- **SC-004**: A suíte de mensageria de um serviço adiciona no máximo cerca de 2 minutos ao tempo de
  `./mvnw verify` desse serviço.
- **SC-005**: Quebrar de propósito um comportamento de mensageria (ex.: remover a chave `orderId` da
  mensagem, ou publicar no tópico errado) faz pelo menos um teste novo falhar, com mensagem que aponta o que
  foi esperado.
- **SC-006**: Zero mudanças no comportamento de produção dos serviços e zero código de teste compartilhado
  entre eles; a única mudança de `pom.xml` por serviço é a dependência de teste.

## Assumptions

- Esta feature é o item E2.6 do ROADMAP e fecha a Fase 2; transforma em testes automatizados a validação
  manual descrita nas seções "Validações empíricas" das specs 014–017.
- O Testcontainers já é usado desde o item 1.11 com Postgres real; esta feature acrescenta o broker Kafka
  real à mesma abordagem e mantém o requisito de que a suíte exija somente um runtime de contêiner.
- O broker de teste usa a mesma imagem do Kafka do ambiente (a do `infra/docker-compose.yml`), para que os
  testes reflitam o comportamento real.
- Os testes compartilham um único broker e um único banco por serviço (para velocidade) e se isolam por
  identificadores únicos, em vez de subir um ambiente por teste.
- Os testes de mensageria ficam em classes próprias, separadas das suítes com broker simulado; a seleção
  entre elas é só por organização, sem perfil especial: ambas rodam em `./mvnw test`.
- Pausar o contêiner do broker é o meio de simular indisponibilidade; o produtor tem timeouts curtos
  configurados desde o E2.2, então a recuperação é rápida.
- O teste ponta a ponta atravessando os três serviços fica fora de escopo (cada serviço é um projeto
  independente e a suíte de cada um não deve depender dos outros).
