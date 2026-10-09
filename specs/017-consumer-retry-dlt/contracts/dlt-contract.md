# Contrato: Dead-Letter Topics dos consumidores

Acréscimo ao contrato geral do E2.1
([event-contract.md](../../013-kafka-event-convention/contracts/event-contract.md)); o texto da seção
"Convenção de dead-letter topic" abaixo é copiado para lá na implementação.

## Convenção de nome

`<tópico-de-origem>.dlt`, tudo minúsculas, **um DLT por tópico de origem consumido**:

| Serviço consumidor | Tópico de origem | DLT |
|---|---|---|
| payment-api | `order.created` | `order.created.dlt` |
| invoice-api | `payment.reserved` | `payment.reserved.dlt` |

Cada serviço declara explicitamente o seu DLT (3 partições, 1 réplica). Se um tópico passar a ter mais
de um consumidor, a convenção será revisitada (hoje cada tópico tem um único consumidor).

## O que vai ao DLT

| Situação | Retries | Destino |
|---|---|---|
| Conteúdo ilegível, campo obrigatório ausente/inválido, valor nulo | nenhum | DLT imediatamente |
| Falha transitória que não se resolve em `1 + max-retries` tentativas | todos | DLT ao esgotar |
| Falha transitória que se resolve dentro do limite | alguns | nunca vai ao DLT |

Nada é descartado: o único destino de uma mensagem não processada é o DLT.

## Formato da mensagem no DLT

- **Chave** e **valor**: idênticos aos da mensagem original (a chave define a partição do DLT).
- **Headers de diagnóstico** (padrão spring-kafka): `kafka_dlt-original-topic`,
  `kafka_dlt-original-partition`, `kafka_dlt-original-offset`, `kafka_dlt-original-consumer-group`,
  `kafka_dlt-original-timestamp`, `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-cause-fqcn`,
  `kafka_dlt-exception-message`, `kafka_dlt-exception-stacktrace`.
- Sem header de tipo Java (`__TypeId__`), como nos demais tópicos.

## Garantias

- **Pelo menos uma vez**: se o envio ao DLT falhar, a mensagem é reentregue (nunca perdida).
- **Ordem**: ao estacionar uma mensagem, as seguintes seguem; a ordem entre a estacionada e as
  posteriores não é garantida. A idempotência por `eventId` torna o reprocessamento seguro.
- **Retenção**: padrão do broker; sem consumidor nem limpeza próprios.

## Reprocessamento manual (DLT → origem)

Procedimento (sem ferramenta nova), detalhado e validado no
[quickstart](../quickstart.md): consumir o DLT com chave e valor, reenviar cada par ao tópico de origem
com `kafka-console-producer.sh --property parse.key=true`. Mensagens já processadas antes são ignoradas
como duplicata; mensagens ainda inválidas voltam ao DLT.
