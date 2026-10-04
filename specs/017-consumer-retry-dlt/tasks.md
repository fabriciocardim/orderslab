---

description: "Task list — estratégia de erro de consumo: retry limitado e DLT (E2.5)"
---

# Tasks: Estratégia de Erro de Consumo (Retry Limitado e DLT)

**Input**: Design documents from `/specs/017-consumer-retry-dlt/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/dlt-contract.md, quickstart.md

**Tests**: INCLUÍDOS — FR-014 exige cobertura da classificação, do limite/crescimento das esperas, do destino ao DLT e da não-perda.

**Organization**: por user story (US1 = retry limitado + DLT para falha transitória; US2 = mensagem inválida direto ao DLT; US3 = reprocessamento manual do DLT).

## Format: `[ID] [P?] [Story] Description`

Caminhos relativos à raiz do repo. Cada serviço tem a **sua cópia** (Princípio I): faça no `payment-api` e porte para o `invoice-api` trocando pacote/nomes (`com.orderslab.payment_api`→`com.orderslab.invoice_api`, `OrderCreated*`→`PaymentReserved*`, `order.created`→`payment.reserved`); nunca importe de um serviço no outro. Abreviações: `<P>` = `payment-api/src/main/java/com/orderslab/payment_api`, `<I>` = `invoice-api/src/main/java/com/orderslab/invoice_api`, `<PT>`/`<IT>` = os respectivos `src/test/java/...`. Maven em cada serviço (`./mvnw`). **Docker precisa estar ligado.** `order-api` NÃO é alterado.

---

## Phase 1: Setup

- [X] T001 Rodar `./mvnw clean verify` em `payment-api/` e em `invoice-api/` e confirmar `BUILD SUCCESS` como linha de base; registrar em `specs/017-consumer-retry-dlt/research.md` seção "Validações empíricas (implementação)".

---

## Phase 2: Foundational (bloqueia todas as user stories)

- [X] T002 [P] Criar `<P>/consumer/InvalidMessageException.java` e `<I>/consumer/InvalidMessageException.java`: `public class InvalidMessageException extends RuntimeException` com construtor `(String message)` e `(String message, Throwable cause)` — representa falha **permanente** (conteúdo inválido), não-retentável.
- [X] T003 [P] Criar `<P>/config/ConsumerRetryProperties.java` e `<I>/config/ConsumerRetryProperties.java`: `@ConfigurationProperties("consumer.retry")`, record com `maxRetries` int `@DefaultValue("4")`, `initialIntervalMs` long `@DefaultValue("1000")`, `multiplier` double `@DefaultValue("2.0")`, `maxIntervalMs` long `@DefaultValue("10000")`; registrar em `<P>/config/SchedulingConfig.java` e `<I>/config/SchedulingConfig.java` (`@EnableConfigurationProperties({..., ConsumerRetryProperties.class})`).
- [X] T004 [P] Adicionar o `NewTopic` do DLT (3 partições, 1 réplica, constante pública do nome em minúsculas) em `<P>/config/KafkaTopicsConfig.java` (`order.created.dlt`, constante `ORDER_CREATED_DLT`) e em `<I>/config/KafkaTopicsConfig.java` (`payment.reserved.dlt`, constante `PAYMENT_RESERVED_DLT`).
- [X] T005 Editar `payment-api/src/main/resources/application.properties` e `invoice-api/src/main/resources/application.properties` acrescentando: `consumer.retry.max-retries=4`, `consumer.retry.initial-interval-ms=1000`, `consumer.retry.multiplier=2.0`, `consumer.retry.max-interval-ms=10000` e `spring.datasource.hikari.connection-timeout=5000` (falha de banco rápida — Decisão 6).

**Checkpoint**: exceção, propriedades, DLT declarado e timeout de pool prontos.

---

## Phase 3: User Story 1 — Falha transitória não trava o consumo nem perde mensagens (P1) 🎯 MVP

**Goal**: retry limitado com backoff exponencial e, ao esgotar, estacionamento no DLT com chave/valor/contexto preservados; DLT indisponível não perde a mensagem.

**Independent Test**: quickstart cenários 1, 3, 4 e 7 contra Kafka/Postgres reais.

### Tests for User Story 1

- [X] T006 [P] [US1] Reescrever `<PT>/consumer/KafkaConsumerConfigTest.java` (e portar para `<IT>/consumer/KafkaConsumerConfigTest.java`) exercitando o **handler real** via `DefaultErrorHandler.handleOne(...)` com `Consumer` e `MessageListenerContainer` mockados e `KafkaTemplate<String,String>` mockado (`send(ProducerRecord)` devolve `CompletableFuture` concluído): (a) `RuntimeException` transitória → as 4 primeiras chamadas retornam sem recuperar e **nenhuma** chamada ao template; a 5ª recupera e chama `kafkaTemplate.send` com tópico `<origem>.dlt`; (b) o `ProducerRecord` enviado tem **chave e valor idênticos** ao original e headers `kafka_dlt-original-topic/-partition/-offset` e `kafka_dlt-exception-message`; (c) as esperas do `BackOff` configurado crescem (1000, 2000, 4000, 8000) e respeitam o teto (`ConsumerRetryProperties` com `maxIntervalMs` menor); (d) partição de destino `-1` (escolhida pela chave).
- [X] T007 [P] [US1] Em `<PT>/consumer/KafkaConsumerConfigTest.java` e `<IT>/consumer/KafkaConsumerConfigTest.java`: se o `KafkaTemplate.send` do DLT falhar (future falhado), `handleOne` **não** considera o registro recuperado (a mensagem não é perdida: o registro será reentregue) — FR-007.
- [X] T008 [US1] Em `<PT>/consumer/OrderCreatedListenerTest.java`/`<IT>/consumer/PaymentReservedListenerTest.java`: manter o teste "exceção do processador propaga" (transitória) — deve continuar passando sem alteração.

### Implementation for User Story 1

- [X] T009 [US1] Reescrever `<P>/config/KafkaConsumerConfig.java` (e portar para `<I>/config/KafkaConsumerConfig.java`): injetar `KafkaTemplate<String, String>`, `ConsumerRetryProperties` e `JsonMapper`; montar `ExponentialBackOffWithMaxRetries(maxRetries)` com `setInitialInterval/setMultiplier/setMaxInterval`; `DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> new TopicPartition(record.topic() + ".dlt", -1))` (partição `-1` ⇒ escolhida pela chave); `DefaultErrorHandler(recoverer, backOff)`; `addNotRetryableExceptions(InvalidMessageException.class)`; `setRetryListeners(...)` com um `RetryListener` que loga com `log.atWarn()` cada `failedDelivery` (tentativa N + causa), `log.atError()` em `recovered` ("enviada ao DLT") e em `recoveryFailed` ("DLT indisponível"), todos com `topic`, `partition`, `offset`, `attempt`/causa e, **quando legíveis**, `orderId`/`eventId` extraídos por um parse tolerante do valor (qualquer falha de parse é ignorada, nunca lança). Manter o bean `public CommonErrorHandler kafkaErrorHandler(...)` (o Boot o aplica ao container) e **remover** `transientFailureBackOff()`.
- [X] T010 [US1] Rodar `./mvnw test` em `payment-api/` e `invoice-api/` — T006–T008 verdes e o resto da suíte intacto.
- [X] T011 [US1] Validação empírica (quickstart cenários 1, 3, 4 e 7): subir os serviços; `order.created.dlt`/`payment.reserved.dlt` com 3 partições; Postgres parado + 3 mensagens → logs de tentativas 1..5 com esperas crescentes e as 3 no DLT em ≈ 40 s sem perda, e o serviço retoma com uma mensagem nova; banco religado em ~10 s → processada sem ir ao DLT; logs com `traceId`. Registrar em research.md.

**Checkpoint**: US1 funcional (MVP).

---

## Phase 4: User Story 2 — Mensagem inválida é estacionada, não apenas logada (P2)

**Goal**: conteúdo inválido vai direto ao DLT, sem retries, com a mensagem original preservada.

**Independent Test**: quickstart cenário 2.

### Tests for User Story 2

- [X] T012 [P] [US2] Em `<PT>/consumer/OrderCreatedListenerTest.java` e `<IT>/consumer/PaymentReservedListenerTest.java`: o teste "descarta ilegível/incompleto sem lançar" passa a esperar **`InvalidMessageException`** (ilegível, `{}`, campos obrigatórios ausentes, `orderId`/`paymentId` não-UUID, `amount <= 0`, valor nulo) e o processador **nunca** chamado.
- [X] T013 [P] [US2] Em `<PT>/consumer/KafkaConsumerConfigTest.java` e `<IT>/consumer/KafkaConsumerConfigTest.java`: `InvalidMessageException` vai ao DLT **na 1ª chamada** de `handleOne` (sem retry), com chave/valor/headers preservados.

### Implementation for User Story 2

- [X] T014 [US2] Editar `<P>/consumer/OrderCreatedListener.java` e `<I>/consumer/PaymentReservedListener.java`: onde hoje há `discard(...)` + `return null`, lançar `new InvalidMessageException(<motivo>, <causa ou null>)` (parse falhou, campo obrigatório ausente/inválido, valor nulo); remover o log de descarte (o `RetryListener` agora loga o envio ao DLT com origem e causa); o fluxo válido e a propagação das exceções do processador ficam como estão.
- [X] T015 [US2] Rodar `./mvnw test` nos dois serviços — T012–T013 verdes.
- [X] T016 [US2] Validação empírica (quickstart cenário 2): ilegível, sem `orderId` e `amount` 0 → DLT **sem** logs de retry, chave/valor idênticos, headers `kafka_dlt-*`; a mensagem válida seguinte é processada; serviço `UP`. Em ambos os serviços. Registrar em research.md.

**Checkpoint**: US1 + US2 verificadas.

---

## Phase 5: User Story 3 — Reprocessar mensagens do DLT sem duplicar (P3)

**Goal**: procedimento documentado e comprovado de DLT → tópico de origem, com convergência pela idempotência.

**Independent Test**: quickstart cenário 6.

### Tests for User Story 3

- [X] T017 [P] [US3] Em `<PT>/PaymentApiApplicationTests.java`-equivalente já existente (`payment-api/src/test/java/com/orderslab/payment_api/PaymentApiApplicationTests.java`) e `invoice-api/.../InvoiceApiApplicationTests.java`: confirmar que os testes de idempotência existentes (reentrega do mesmo evento, dois `eventId` no mesmo pedido) seguem verdes — são a garantia que torna o reprocessamento seguro; se algo quebrar por causa da nova config, corrigir (sem criar teste novo).

### Implementation for User Story 3

- [X] T018 [US3] Validar empiricamente o procedimento de reprocessamento (quickstart cenário 6) com as mensagens estacionadas do cenário 3: ler o DLT (chave e valor), reenviar cada par ao tópico de origem com `kafka-console-producer.sh --property parse.key=true --property key.separator='#'`; confirmar 1 pagamento/nota e 1 evento de saída por pedido, que reenviar uma já processada gera `Duplicate ... ignored` (0 duplicatas) e que uma ainda inválida volta ao DLT. Ajustar a redação do procedimento em `specs/017-consumer-retry-dlt/quickstart.md` conforme o que de fato funcionou e registrar em research.md.
- [X] T019 [US3] Validação empírica da não-perda com DLT indisponível (quickstart cenário 5): registrar o observado em research.md (o teste T007 cobre o comportamento do handler; aqui, o efeito no serviço real).

**Checkpoint**: todas as stories entregues.

---

## Phase 6: Polish & Cross-Cutting

- [X] T020 Rodar `./mvnw clean verify` e `./mvnw pmd:pmd` (0 violações) em `payment-api/` e `invoice-api/`; confirmar `PaymentControllerTest`/`PaymentServiceTest`/`InvoiceControllerTest`/`InvoiceServiceTest` e os testes dos eventos de saída verdes **sem edição** (FR-011, SC-007).
- [X] T021 [P] Acrescentar a seção "Convenção de dead-letter topic" em `specs/013-kafka-event-convention/contracts/event-contract.md` (convenção `<tópico>.dlt`, tabela dos dois DLTs, o que vai ao DLT, formato/headers, garantias e link para `specs/017-consumer-retry-dlt/contracts/dlt-contract.md`) e remover da seção "O que fica para itens futuros" a linha de "Estratégia de retry/dead-letter (E2.5)".
- [X] T022 [P] Atualizar as specs 015 e 016 só onde descrevem a política interina como definitiva (`research.md` Decisão 4/riscos): acrescentar uma nota "substituída pelo E2.5 (spec 017)" — sem reescrever o histórico.
- [X] T023 [P] Atualizar `ROADMAP.md` marcando E2.5 como ✅ concluído (data, resumo, decisões-chave e link para `specs/017-consumer-retry-dlt`).
- [X] T024 [P] Verificar paridade Compose×k8s (Princípio IV): nenhuma variável nova obrigatória; propriedades padrão em `application.properties`; registrar em research.md. Confirmar com `git diff --stat` que `order-api/` e os `pom.xml` não mudaram (FR-012, SC-008) e marcar todas as tasks acima como `[X]`.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 (bloqueia tudo) → US1 → US2 → US3 → Polish.
- US2 depende de US1 (o handler e o DLT); US3 depende de US1/US2 (precisa de mensagens no DLT).
- Dentro de US1: T006–T008 antes de T009. T006/T007 editam o mesmo arquivo — agrupar em uma edição única.
- Paralelo: Foundational T002–T004 (arquivos distintos nos dois serviços); Polish T021–T024.

## Implementation Strategy

- **MVP**: Phases 1–3 (US1) — retry limitado + DLT para falha transitória.
- Incremental: US2 fecha o descarte silencioso (inválido → DLT); US3 comprova o ciclo operacional (reprocessar).
- Parar e validar a cada checkpoint (`./mvnw test` nos dois serviços).
