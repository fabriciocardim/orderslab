# Quickstart: Instrumentação Básica com OpenTelemetry

Como validar traces distribuídos + logs estruturados correlacionáveis, sem nenhum Collector
rodando (situação normal neste item — ver Assumptions da spec).

## Pré-requisitos

Nenhum — deliberadamente. O ponto desta feature é funcionar mesmo sem infra de
observabilidade nenhuma no ar.

## Passo a passo (exemplo com order-api, mesmo padrão para os outros 2)

```bash
cd order-api && ./mvnw spring-boot:run
```

Esperado: o serviço sobe normalmente (FR-006/SC-003) — nenhum erro de startup, mesmo sem
nenhum OTel Collector escutando em `localhost:4318`.

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-1","amount":100}'
```

## Verificação: trace distribuído gerado (SC-001)

Nos logs do console do serviço, ao redor da requisição acima, deve aparecer um JSON
estruturado (ECS) contendo um campo de identificador de trace preenchido — confirma que a
requisição HTTP gerou um trace real.

## Verificação: log de transição de estado correlacionado ao trace (SC-002)

No mesmo log gerado pela chamada `create` do `Service` (ponto de transição de estado), o
identificador de trace deve ser **o mesmo** da requisição HTTP que o originou — confirma a
correlação log↔trace sem precisar de nenhum backend.

```bash
# confirmar visualmente comparando o trace.id do log de acesso HTTP com o do log do Service
```

## Verificação: métricas básicas expostas (FR-005)

```bash
curl -s http://localhost:8080/actuator/metrics | python3 -m json.tool | head -20
curl -s http://localhost:8080/actuator/metrics/http.server.requests | python3 -m json.tool
```

Esperado: a lista de métricas disponíveis inclui `http.server.requests`, com pelo menos uma
medição registrada após a requisição de criação acima.

## Verificação: 100% dos cenários anteriores continuam passando (SC-004)

```bash
./mvnw test
```

Esperado: `BUILD SUCCESS`, 22 testes passando — comportamento idêntico ao dos itens
1.6-1.11, agora com instrumentação ativa.

## Repetir para os outros 2 serviços

```bash
cd ../payment-api && ./mvnw spring-boot:run
cd ../invoice-api && ./mvnw spring-boot:run
```
