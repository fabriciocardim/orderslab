# Quickstart: Convenção de Evento/Tópico Kafka

Como validar a decisão — sem nenhum produtor/consumidor real ainda (isso é E2.2-E2.4).

## Verificação 1: mecanismo de serialização existe no classpath dos 3 serviços

```bash
for s in order-api payment-api invoice-api; do
  echo "=== $s ==="
  cd "$s" && ./mvnw dependency:tree -Dincludes=org.springframework.kafka:spring-kafka 2>&1 | grep spring-kafka
  cd ..
done
```

Esperado: `spring-kafka:4.1.1` presente nos 3 (via `spring-boot-starter-kafka`, já desde o
item 1.1) — confirma que `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Decisão 1) já
estão disponíveis sem nenhuma dependência nova.

## Verificação 2: contrato cobre os 6 eventos sem ambiguidade (SC-001/SC-003)

Percorrer manualmente `contracts/event-contract.md` e confirmar, para cada um dos 6 eventos
já nomeados no ROADMAP, que a tabela de tópicos determina um nome único e sem ambiguidade.

## Verificação 3: `orderId` presente mesmo em eventos que não são sobre pedido (SC-002)

Inspecionar o envelope documentado e confirmar que `PaymentReserved`/`PaymentFailed`/
`InvoiceIssued`/`InvoiceFailed` (eventos de `payment-api`/`invoice-api`) exigem `orderId` no
mesmo nível que `OrderCreated`/`OrderConfirmed`/`OrderCancelled` — não um campo opcional ou
ausente.

## Verificação 4: zero dependência de código compartilhada (SC-004)

```bash
git status
```

Esperado: nenhum `pom.xml`/arquivo de código de produção dos 3 serviços aparece como
modificado — esta feature só adiciona documentação em
`specs/013-kafka-event-convention/`.
