# Quickstart: rodar e validar a suíte de mensageria

## Pré-requisito

Só um runtime de contêiner (Docker). Nada de Compose do projeto: a suíte sobe o próprio Postgres e o próprio
Kafka (`apache/kafka:4.2.0`, baixado na primeira vez).

## Rodar

```bash
cd order-api   && ./mvnw test -Dtest='OrderMessagingTest'   # US1
cd payment-api && ./mvnw test -Dtest='PaymentMessagingTest' # US2
cd invoice-api && ./mvnw test -Dtest='InvoiceMessagingTest' # US3
cd <serviço>   && ./mvnw clean verify                       # suíte completa + PMD
```

## Validar a estabilidade (SC-003)

Repetir a suíte de mensageria 10× por serviço:
`for i in 1 2 3 4 5 6 7 8 9 10; do ./mvnw -q test -Dtest='<Classe>' || break; done` — sem falhas e sem limpeza.

## Validar que os testes detectam regressões (SC-005)

Mutação 1 (chave): em `src/main`, trocar temporariamente a chave de publicação do relay por `null`/outro valor →
`<Classe>` deve falhar apontando "chave". Mutação 2 (tópico): publicar no tópico errado → falha apontando o tópico
esperado. **Reverter** a mutação (`git checkout -- <arquivo>`) e rodar de novo até passar.

## Validar o tempo (SC-004)

Comparar o tempo de `./mvnw clean verify` antes (linha de base registrada) e depois; acréscimo ≤ ~2 min.
