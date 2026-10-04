---

description: "Task list — testes de mensageria com Testcontainers Kafka (E2.6)"
---

# Tasks: Testes de Mensageria com Kafka Real (Testcontainers)

**Input**: Design documents from `/specs/018-messaging-testcontainers/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: ESTA FEATURE É TESTE — todas as tasks de implementação produzem código de teste. Nada em `src/main` muda (FR-013).

**Organization**: por user story (US1 = order-api; US2 = payment-api; US3 = invoice-api). Cada serviço é independente.

## Format: `[ID] [P?] [Story] Description`

Caminhos relativos à raiz do repo. `<OT>` = `order-api/src/test/java/com/orderslab/order_api/messaging`, `<PT>` = `payment-api/src/test/java/com/orderslab/payment_api/messaging`, `<IT>` = `invoice-api/src/test/java/com/orderslab/invoice_api/messaging`. **Docker precisa estar ligado.** Cada serviço tem o **seu** `KafkaTestSupport` (cópia, pacote próprio) — nunca compartilhar código de teste entre serviços (Princípio I).

---

## Phase 1: Setup

- [X] T001 Rodar `./mvnw clean verify` em `order-api/`, `payment-api/` e `invoice-api/`, anotar o **tempo** de cada um (linha de base para SC-004) e confirmar `BUILD SUCCESS`; registrar em `specs/018-messaging-testcontainers/research.md` seção "Validações empíricas (implementação)".

---

## Phase 2: Foundational (bloqueia todas as user stories)

- [X] T002 [P] Acrescentar a `order-api/pom.xml`, `payment-api/pom.xml` e `invoice-api/pom.xml`, junto às demais dependências de teste, `<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-kafka</artifactId><scope>test</scope></dependency>` (**sem versão**: o BOM do Boot gerencia a 2.0.5). Única mudança permitida em pom (FR-012).
- [X] T003 [P] Criar `<OT>/KafkaTestSupport.java` (e cópias próprias `<PT>/KafkaTestSupport.java`, `<IT>/KafkaTestSupport.java`, trocando só o pacote): classe utilitária de teste com (a) `KafkaConsumer<String,String> consumerFor(String bootstrapServers, String topic)` — `StringDeserializer`, `group.id` aleatório, `enable.auto.commit=false`, `assign` de todas as partições (via `partitionsFor`) e `seekToBeginning`; (b) `List<ConsumerRecord<String,String>> awaitRecords(String bootstrapServers, String topic, String key, int expected, Duration timeout)` — polling em laço até o prazo, acumula só registros com a chave dada, retorna quando atinge `expected`, e no estouro lança `AssertionError("esperava N registro(s) com chave K em <tópico> em T; chegaram M")`; (c) `void assertNoMore(String bootstrapServers, String topic, String key, int expectedTotal, Duration quietPeriod)` — lê por `quietPeriod` e falha se houver mais registros da chave que o esperado; (d) `Set<String> headerNames(ConsumerRecord<?,?> record)`; (e) `int partitionCount(String bootstrapServers, String topic)` via `AdminClient.describeTopics`. Sem pausas fixas como sincronização (FR-009).
- [X] T004 Confirmar que a imagem sobe: escrever, em `<OT>/OrderMessagingTest.java` (esqueleto), apenas os contêineres `@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")` e `static org.testcontainers.kafka.KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.0")`, com `@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"outbox.relay.interval-ms=200"})`, `@AutoConfigureRestTestClient`, `@Testcontainers` e um `contextLoads()`; rodar `./mvnw test -Dtest=OrderMessagingTest` — confirma que o `@ServiceConnection` injeta o bootstrap (Decisão 8.1). Registrar em research.md.

**Checkpoint**: dependência, auxiliar de teste e ambiente Kafka real funcionando.

---

## Phase 3: User Story 1 — order-api publica os eventos corretos num Kafka real (P1) 🎯 MVP

**Goal**: automatizar a validação manual do E2.2 no `order-api`.

**Independent Test**: `cd order-api && ./mvnw test -Dtest=OrderMessagingTest`.

- [X] T005 [US1] Em `<OT>/OrderMessagingTest.java`: teste `eachOperationPublishesExactlyOneEventOnTheRightTopic` — cria um pedido (`POST /api/orders`, `{"customerId":"c","amount":10.50}`), confirma-o, cria outro e o cancela; para cada tópico (`order.created` ×2, `order.confirmed` ×1, `order.cancelled` ×1) usa `awaitRecords(..., Duration.ofSeconds(30))` filtrando pela chave = `orderId`; verifica 1 registro por operação, **chave = orderId**, **sem header `__TypeId__`** (`headerNames` não o contém), JSON com `eventId` (UUID), `eventType` exato (`OrderCreated`/`OrderConfirmed`/`OrderCancelled`), `eventVersion` = 1, `occurredAt` ISO-8601 UTC, `orderId`, e `customerId`/`amount` só no `OrderCreated`; depois `assertNoMore` (silêncio de 1 s) para provar que não há duplicata.
- [X] T006 [US1] Em `<OT>/OrderMessagingTest.java`: teste `topicsHaveThreePartitions` — `partitionCount` de `order.created`, `order.confirmed` e `order.cancelled` é 3.
- [X] T007 [US1] Em `<OT>/OrderMessagingTest.java`: teste `createdEventPrecedesConfirmedEventPerOrder` — cria e confirma 5 pedidos em sequência; para cada pedido compara `timestamp()` do registro em `order.created` com o de `order.confirmed` (criação ≤ confirmação).
- [X] T008 [US1] Em `<OT>/OrderMessagingTest.java`: teste `brokerDownDoesNotAffectHttpAndEventsArriveAfterRecovery` — pausa o contêiner (`kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()`); dentro de `try/finally` com `unpauseContainerCmd` no `finally` (FR-003): cria 3 pedidos e confirma 1 (todas as respostas 201/200 normais), e confirma por JDBC (`JdbcTemplate`) que `outbox_events` ainda tem linhas desses `orderId`; ao retomar o broker, `awaitRecords(..., Duration.ofSeconds(60))` recebe exatamente os eventos esperados (3 em `order.created`, 1 em `order.confirmed`) e `assertNoMore` confirma 0 duplicatas, com o outbox esvaziando.
- [X] T009 [US1] Rodar `./mvnw test -Dtest=OrderMessagingTest` em `order-api/` — todos verdes; depois `./mvnw clean verify` + `./mvnw pmd:pmd` (0 violações) e medir o tempo (SC-004). Registrar em research.md.

**Checkpoint**: US1 entregue.

---

## Phase 4: User Story 2 — payment-api consome e publica num Kafka real (P2)

**Goal**: automatizar a validação manual do E2.3 e do DLT do E2.5 no `payment-api`.

**Independent Test**: `cd payment-api && ./mvnw test -Dtest=PaymentMessagingTest`.

- [X] T010 [US2] Criar `<PT>/PaymentMessagingTest.java`: contêineres como em T004, `@SpringBootTest(RANDOM_PORT, properties = {"outbox.relay.interval-ms=200", "consumer.retry.initial-interval-ms=3000"})` (retry lento **de propósito**: provar "DLT sem retry"), `@Autowired KafkaTemplate<String,String>` (real) para publicar e `JdbcTemplate`; helper `orderCreatedJson(eventId, orderId, amount)` seguindo o contrato do E2.2 e helper `publish(key, value)` que faz `send(...).get()`.
- [X] T011 [US2] Em `<PT>/PaymentMessagingTest.java`: `lowAmountBecomesReservedAndPublishedOnPaymentReserved` e `highAmountBecomesFailedAndPublishedOnPaymentFailed` — publica `OrderCreated` com valor 10.50 e outro com 1500; `awaitRecords` em `payment.reserved`/`payment.failed` (chave = `orderId`, 30 s): 1 registro, `eventType` `PaymentReserved`/`PaymentFailed`, `paymentId`+`amount` / `reason` `AMOUNT_LIMIT_EXCEEDED`, sem `__TypeId__`; JDBC confirma pagamento `RESERVED`/`FAILED` do `orderId`; exatamente no limite (1000) → `RESERVED`.
- [X] T012 [US2] Em `<PT>/PaymentMessagingTest.java`: `redeliveryOfTheSameEventDoesNotDuplicate` — publica o **mesmo** `OrderCreated` (mesmo `eventId`) 3×, e outro com `eventId` novo e o mesmo `orderId`; `awaitRecords` espera 1 evento em `payment.reserved` e `assertNoMore` confirma que não surge outro; JDBC: exatamente 1 pagamento do `orderId`.
- [X] T013 [US2] Em `<PT>/PaymentMessagingTest.java`: `invalidMessagesGoStraightToTheDltAndTheNextOneIsProcessed` — publica em `order.created` (chaves `bad-<uuid>` próprias) `lixo-nao-json`, `{"eventId":"x","amount":5}`, um JSON sem `orderId` e um com `amount` 0, e **depois** um `OrderCreated` válido; para cada inválida, `awaitRecords` em `order.created.dlt` (prazo 10 s, **bem abaixo** do tempo que o retry lento levaria) com chave e valor **idênticos** ao enviado e headers `kafka_dlt-original-topic` (= `order.created`), `kafka_dlt-original-offset` e `kafka_dlt-exception-message` presentes; a válida vira `PaymentReserved` (processada normalmente).
- [X] T014 [US2] Em `<PT>/PaymentMessagingTest.java`: `outputAndDltTopicsHaveThreePartitions` — `partitionCount` de `payment.reserved`, `payment.failed` e `order.created.dlt` é 3.
- [X] T015 [US2] Rodar `./mvnw test -Dtest=PaymentMessagingTest` em `payment-api/` — verdes; `./mvnw clean verify` + `pmd:pmd` (0 violações); medir tempo. Registrar em research.md.

**Checkpoint**: US2 entregue.

---

## Phase 5: User Story 3 — invoice-api consome e publica num Kafka real (P3)

**Goal**: automatizar a validação manual do E2.4 e do DLT do E2.5 no `invoice-api`.

**Independent Test**: `cd invoice-api && ./mvnw test -Dtest=InvoiceMessagingTest`.

- [X] T016 [US3] Criar `<IT>/InvoiceMessagingTest.java` espelhando T010–T014 do `payment-api` (copiar e adaptar, sem importar dele): tópico de entrada `payment.reserved`; helper `paymentReservedJson(eventId, orderId, paymentId, amount)`; testes `lowAmountBecomesIssuedAndPublishedOnInvoiceIssued` (valor 10.50 e exatamente 500 → `InvoiceIssued` com `invoiceId`/`paymentId`/`amount`), `highAmountBecomesFailedAndPublishedOnInvoiceFailed` (750 → `InvoiceFailed`, `reason` `AMOUNT_ABOVE_ISSUANCE_LIMIT`, com `paymentId`), `redeliveryOfTheSameEventDoesNotDuplicate`, `invalidMessagesGoStraightToTheDltAndTheNextOneIsProcessed` (DLT `payment.reserved.dlt`, incluindo `paymentId` ausente/não-UUID), `outputAndDltTopicsHaveThreePartitions` (`invoice.issued`, `invoice.failed`, `payment.reserved.dlt`); propriedades de teste `outbox.relay.interval-ms=200` e `consumer.retry.initial-interval-ms=3000`; JDBC em `invoices`.
- [X] T017 [US3] Rodar `./mvnw test -Dtest=InvoiceMessagingTest` em `invoice-api/` — verdes; `./mvnw clean verify` + `pmd:pmd` (0 violações); medir tempo. Registrar em research.md.

**Checkpoint**: US3 entregue; Fase 2 fecha com os três serviços cobertos.

---

## Phase 6: Polish & Cross-Cutting

- [X] T018 Estabilidade (SC-003): rodar a suíte de mensageria de cada serviço **10 vezes seguidas** (`for i in 1 2 3 4 5 6 7 8 9 10; do ./mvnw -q test -Dtest='<Classe>' || break; done`) sem falha e sem limpeza manual; se algum teste for intermitente, corrigir a causa (não aumentar pausa fixa) e repetir. Registrar o resultado em research.md.
- [X] T019 Mutação (SC-005): em `order-api`, trocar temporariamente em `src/main` a chave do `send` do relay e confirmar que `OrderMessagingTest` falha apontando a chave; em `payment-api`, trocar temporariamente o tópico de saída e confirmar que `PaymentMessagingTest` falha apontando o tópico; **reverter** ambas (`git checkout -- <arquivo>`), confirmar `git status` sem mudanças em `src/main` e que os testes voltam a passar. Registrar em research.md.
- [X] T020 Confirmar `git diff --stat`: em cada serviço só mudam `pom.xml` (uma dependência `test`) e arquivos novos em `src/test/.../messaging`; **nenhuma** mudança em `src/main` (FR-013, SC-006); suítes existentes intactas e verdes (FR-011).
- [X] T021 [P] Atualizar `ROADMAP.md` marcando E2.6 como ✅ concluído (data, resumo, o que cada suíte cobre, tempos, estabilidade e mutação) e acrescentar uma nota curta de que a **Fase 2 está concluída**; atualizar a seção de ordem recomendada/status do ROADMAP se citar a Fase 2 como pendente.
- [X] T022 [P] Marcar todas as tasks acima como `[X]`.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 (bloqueia tudo) → US1 → US2 → US3 → Polish. US2 e US3 não dependem de US1 além do padrão (podem ser feitas em qualquer ordem após a Phase 2).
- Dentro de cada story: contêineres e helpers antes dos testes; os testes de uma mesma classe editam o mesmo arquivo — agrupar em edições únicas.
- Paralelo: T002/T003 (arquivos distintos); T021/T022.

## Implementation Strategy

- **MVP**: Phases 1–3 (US1) — padrão de teste com Kafka real no `order-api`.
- Incremental: US2 e US3 replicam o padrão para os consumidores+produtores; Polish prova estabilidade e poder de detecção.
- Parar e validar a cada checkpoint (rodar a classe de mensageria do serviço).
