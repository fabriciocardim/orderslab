# Data Model: Estratégia de Erro de Consumo (Retry Limitado e DLT)

**Sem mudança de esquema e sem migração.** Nenhuma tabela nova, nenhuma coluna nova: a idempotência do
E2.3/E2.4 (`source_event_id` + índices únicos) é o que torna o reprocessamento do DLT seguro.

## Mensagem morta (registro no DLT)

Uma mensagem estacionada é uma cópia da original em `<tópico-de-origem>.dlt`:

| Parte | Conteúdo |
|---|---|
| Tópico | `order.created.dlt` (payment-api) / `payment.reserved.dlt` (invoice-api) |
| Chave | **a mesma** da original (o `orderId`); define a partição do DLT |
| Valor | **idêntico** ao original, byte a byte (texto UTF-8; pode ser JSON ilegível; nulo se o original era nulo) |
| Headers `kafka_dlt-original-topic` / `-partition` / `-offset` | origem da mensagem |
| Headers `kafka_dlt-original-consumer-group` / `-timestamp` | grupo consumidor e instante original |
| Headers `kafka_dlt-exception-fqcn` / `-cause-fqcn` / `-message` / `-stacktrace` | causa da falha |

## Política de retry (configuração, não dado persistido)

`consumer.retry.*` em cada serviço:

| Propriedade | Padrão | Significado |
|---|---|---|
| `max-retries` | `4` | retentativas após a 1ª falha (5 tentativas no total) |
| `initial-interval-ms` | `1000` | espera antes da 1ª retentativa |
| `multiplier` | `2.0` | fator de crescimento da espera |
| `max-interval-ms` | `10000` | teto da espera |

Esperas padrão: 1 s, 2 s, 4 s, 8 s (≈ 15 s). Falha permanente (`InvalidMessageException`): 0 retentativas.

## Máquina de estados de uma mensagem consumida

```text
recebida ──processada ok──────────────────────────────────────────→ processada (offset avança)
   │
   ├─ conteúdo inválido (InvalidMessageException) ──→ DLT (sem retry) ─→ estacionada (offset avança)
   │
   └─ falha transitória ─ tentativa 1..N ─ ok ──→ processada
                                          └ esgotou ──→ DLT ─→ estacionada (offset avança)
                                                          └ DLT indisponível ──→ reentregue (recomeça o ciclo; offset NÃO avança)
reprocessamento manual: estacionada ──reenviada ao tópico de origem──→ recebida (duplicata ⇒ ignorada pela idempotência)
```
