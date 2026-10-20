# Research: Limites de Memória da Stack

## Evidência (incidente do T1, 2026-10-18)

htop da VPS durante o perfil `load`: 2 vCPU a 100%, **7,60/7,75 GB**, swap 0K/0K, load average 310; RES de poucos KB em
dockerd/traefik/JVMs (páginas de código despejadas, thrashing); Jaeger all-in-one com ~980 MB e crescendo. A VPS
precisou de reboot. Nenhum container tinha `mem_limit`; as JVMs não tinham `-Xmx` nem `MaxRAMPercentage` (padrão:
até 1/4 da RAM do **host** cada); `management.tracing.sampling.probability=1.0`; `spring.jpa.show-sql=true`.

## Decisão 1 — Orçamento de memória (Compose, máquina de 8 GB)

| Componente | Limite | Ajuste interno |
|---|---|---|
| order-service, payment-service, invoice-service | 768 MB cada | `-XX:MaxRAMPercentage=65` (~500 MB de heap) |
| kafka | 1 GB | `KAFKA_HEAP_OPTS=-Xms512m -Xmx512m` |
| postgres-api | 768 MB | padrão (`shared_buffers` 128 MB) |
| jaeger | 512 MB | `MEMORY_MAX_TRACES=20000` |
| prometheus | 384 MB | retenção de 7 dias |
| loki | 384 MB | — |
| kafbat-ui | 384 MB | `-XX:MaxRAMPercentage=65` |
| grafana | 256 MB | — |
| otel-collector | 256 MB | `memory_limiter` (80% do limite, pico 25%) |
| frontend (nginx) | 64 MB | — |
| **Total** | **≈ 6,2 GB** | sobra ~1,5 GB para SO, dockerd e Traefik |

768 MB nas APIs (e não 512 MB): heap de ~500 MB + metaspace (~120 MB) + threads/buffers. Recalibrar com
`load-tests/scripts/monitor.sh` (`containers.csv`).

**Alternativas descartadas**: `-Xmx` fixo (não acompanha o limite se ele mudar); limites só no Kubernetes (a produção
roda Compose).

## Decisão 2 — Amostragem e log de SQL por variável de ambiente, sem mudar código

O Spring Boot já liga `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` e `SPRING_JPA_SHOW_SQL` às propriedades por
relaxed binding. O Compose passa `${TRACING_SAMPLING_PROBABILITY:-1.0}` e `${SPRING_JPA_SHOW_SQL:-false}`: no lab tudo
continua amostrado; em produção basta `TRACING_SAMPLING_PROBABILITY=0.1` no `.env`. Os `application.properties` não
mudam (os testes continuam com o mesmo comportamento).

## Decisão 3 — Contenção no Compose

- `restart: unless-stopped` em todos os serviços: o kernel mata só o container que estourou o limite e o Docker o
  sobe de novo.
- Rotação de log do driver `json-file` (`max-size: 10m`, `max-file: 3`) por âncora YAML `x-logging`: com `show-sql` e
  logs estruturados, os logs cresciam sem limite em disco.

## Decisão 4 — Kubernetes

`resources.requests` (metade do limite) e `resources.limits` iguais ao Compose em cada Deployment; mesmas variáveis
de ambiente. Com 3 réplicas de cada API, o cluster local pede ~3,4 GiB só para as APIs em requests: documentado.

## Decisão 5 — Swap no servidor (fora do repositório)

Recomendado 2–4 GB de swapfile e `vm.swappiness=10` na VPS: transforma o thrashing instantâneo em degradação
gradual e dá tempo ao OOM killer. Documentado no README; não é aplicado pelo repositório.

## Validações empíricas (implementação)

Executado em 2026-10-19:

| Verificação | Resultado |
|---|---|
| `docker compose -f infra/docker-compose.yml config -q` (antes e depois) | ✅ |
| Soma dos `mem_limit` no Compose | 6.336 MB ≈ 6,2 GB (≤ 6,5 GB, SC-001) |
| `kubectl apply --dry-run=client -f infra/k8s/` + parse YAML de todos os manifests | ✅ |
| `otelcol validate` da config com `memory_limiter` (imagem `otel/opentelemetry-collector:0.162.0`) | ✅ |
| Heap efetivo em container de 768 MB (`eclipse-temurin:21-jre-alpine`) | 500 MB com `MaxRAMPercentage=65`; 192 MB sem a opção (padrão 25%) |
| `./mvnw clean verify` nos 3 serviços | ✅ 202 testes (mesma execução da spec 021; nenhum arquivo dos serviços mudou nesta feature) |

Não verificado aqui: a stack completa subindo com os limites (as imagens dos serviços não foram buildadas nesta
máquina) e o comportamento sob carga. Fica para o deploy na VPS e a próxima rodada do `load`, com
`load-tests/scripts/monitor.sh` gravando `containers.csv` para recalibrar os limites.
