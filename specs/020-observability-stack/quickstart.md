# Quickstart: Deploy do Backend de Observabilidade (Collector + Prometheus + Loki + Jaeger + Grafana)

Como validar que os 3 serviços enviam telemetria real (traces, logs, métricas) — via um
Collector genérico, sem acoplamento a um produto específico — e que ela aparece navegável no
Grafana. Ver [research.md](./research.md) para as decisões técnicas.

## Pré-requisitos

- Docker com memória suficiente para os 5 componentes novos (Collector, Jaeger, Prometheus,
  Loki, Grafana) somados ao que já roda (ver Decisão 8 do research.md — máquina de 8 GB pode
  exigir sequenciamento manual).

## Passo 1 — Subir o backend de observabilidade isoladamente

```bash
cd infra && docker compose up -d otel-collector jaeger prometheus loki grafana
```

Esperado: Grafana acessível (porta padrão `3001` ou outra livre — `3000` já é o frontend do
laboratório), com os 3 datasources (Prometheus, Loki, Jaeger) já provisionados, sem nenhuma
configuração manual.

## Passo 2 — Subir os 3 serviços apontando para o Collector

```bash
cd infra && docker compose up -d postgres-api kafka order-service payment-service invoice-service
```

Esperado (FR-008/graceful degradation): os 3 serviços sobem normalmente mesmo que o passo 1
não tivesse rodado — a diferença agora é que a telemetria tem destino real.

## Verificação: trace real no Jaeger via Grafana (SC-001)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-1","amount":100}'
```

No Grafana (Explore → datasource Jaeger), em até ~30 segundos: o trace dessa requisição deve
aparecer, navegável, mostrando o span HTTP.

## Verificação: log correlacionado ao trace, no Loki (SC-002)

No Grafana (Explore → datasource Loki), filtrar por `trace_id` do trace encontrado acima: deve
aparecer o log estruturado da transição de estado (`create`) emitido pelo `Service`.

## Verificação: métricas no Prometheus via Grafana (SC-003)

No Grafana (Explore → datasource Prometheus), consultar `http_server_requests` (ou nome
equivalente exportado via OTLP): deve haver pelo menos uma medição registrada após a requisição
acima.

## Verificação: nenhuma regressão de contrato/mensageria (SC-004)

```bash
cd order-api && ./mvnw clean verify
cd ../payment-api && ./mvnw clean verify
cd ../invoice-api && ./mvnw clean verify
```

Esperado: `BUILD SUCCESS` nos 3, mesma contagem de testes dos itens 1.1-1.12/E2.1-E2.6, sem
nenhuma alteração de comportamento observável.

## Verificação: graceful degradation preservada (FR-008)

```bash
cd infra && docker compose stop otel-collector jaeger prometheus loki grafana
curl -s -X POST http://localhost:8081/api/orders -H "Content-Type: application/json" -d '{"customerId":"cliente-2","amount":50}'
```

Esperado: a requisição continua respondendo normalmente (201) mesmo com todo o backend parado.

## Verificação: troca de backend não exige tocar nos 3 serviços (SC-006)

Editar só `infra/otel-collector-config.yaml` (ex.: trocar o destino do pipeline de traces para
outro endpoint OTLP de teste) e reiniciar apenas o `otel-collector` — confirmar que nenhum dos 3
serviços precisou ser reiniciado, reconfigurado ou recompilado para essa troca ter efeito.

## Passo 3 — Repetir no cluster k8s local

```bash
kubectl apply -f infra/k8s/namespace.yaml
kubectl apply -f infra/k8s/  # inclui otel-collector, jaeger, prometheus, loki, grafana
```

Repetir as mesmas verificações acima via port-forward do Grafana e dos 3 serviços.

## Ao terminar

Parar e remover os contêineres/pods usados para não deixar os 5 componentes consumindo recursos
sem necessidade (mesmo padrão de limpeza já adotado no item E2.6/U1).
