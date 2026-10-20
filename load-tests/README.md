# Teste de carga — Black Friday (k6)

Simula um evento de Black Friday sobre a arquitetura inteira do orderslab. A carga entra pela mesma porta
que o usuário usa (o nginx do frontend) e atravessa:

```
navegador → nginx (SPA + proxy) → order-api → Postgres (orders + outbox) → Kafka order.created
         → payment-api → Postgres (payments + outbox) → Kafka payment.reserved → invoice-api → Postgres (invoices)
```

O k6 mede o lado de fora (latência, erros, tempo até o pedido virar nota). O [`scripts/monitor.sh`](scripts/monitor.sh)
mede o lado de dentro (lag do Kafka, backlog do outbox, conexões do Postgres, CPU/memória de cada contêiner).

## Cenários simulados

| Cenário | Executor | O que faz |
|---|---|---|
| `compradores` | `ramping-arrival-rate` (pedidos/s) | Carrega a SPA, "pensa" 1–3 s, cria o pedido (`POST /api/orders`). Uma fração (`TRACK_RATIO`) fica na tela de acompanhamento e faz o mesmo polling do `useTrackedOrder`: a cada 2 s, `GET /api/orders/{id}` + `GET /api/payments` + `GET /api/invoices`, até o fluxo terminar ou 30 s |
| `visitantes` | `ramping-vus` | Carrega a SPA. 40% ficam na aba **Listagens** (polling das 3 listas completas a cada 2 s), 15% na aba **Saúde**, o resto só olha e sai |
| `sonda` | `constant-arrival-rate` (1 pedido/2 s) | Sempre acompanha o pedido (polling de 1 s, até 120 s) e **continua depois do fim da carga** (dreno), para medir quanto tempo o backlog do Kafka/outbox leva para esvaziar |
| `navegador` | `constant-vus` (opcional, `BROWSER=1`) | Chromium real: abre a UI, preenche o formulário, espera a tela mostrar o fim do fluxo. Gera Web Vitals (LCP, CLS, FCP…) durante o pico |

Os valores dos pedidos seguem as regras atuais dos serviços, para exercitar também os caminhos de falha:
70% até 500 (nota emitida), 20% entre 500 e 1000 (nota falha), 10% acima de 1000 (pagamento falha). Cada fluxo
concluído é conferido contra o desfecho esperado (`fluxo_desfecho_inesperado`), o que detecta mensagem perdida,
duplicada ou processada errado sob carga.

## Perfis

| Perfil | Pico padrão | Duração (carga + dreno) | Para quê |
|---|---|---|---|
| `smoke` | 1 pedido/s, 2 visitantes | ~1,5 min | Conferir que tudo responde **antes** de qualquer teste maior |
| `load` | 10 pedidos/s, 50 visitantes | ~15 min | Dia comum |
| `blackfriday` | 50 pedidos/s, 200 visitantes | ~24 min | Esquenta → abertura das ofertas (rampa de 1 min) → pico de 5 min → metade por 5 min → esfria |
| `spike` | 2× o pico em 10 s | ~15 min | Oferta relâmpago: a fila drena quando a carga volta ao normal? |
| `stress` | degraus até 200 pedidos/s | ~21 min | Achar o ponto de ruptura |
| `soak` | 20 pedidos/s por 1 h | ~72 min | Vazamento de memória, crescimento das listas, disco |

`PEAK` (pedidos/s no pico) e `VISITORS` (visitantes simultâneos no pico) escalam qualquer perfil
(veja [`lib/profiles.js`](lib/profiles.js)).

## Pré-requisitos

- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) v1 ou superior (validado com v2.3.0). No Windows: `winget install k6 --source winget`.
- Para `BROWSER=1`: Chrome/Chromium instalado na máquina que roda o k6.
- Para o monitor e a limpeza: acesso à máquina onde roda o `docker compose` do orderslab.

## Como rodar

1. **Monitor** (na máquina do docker compose, em outro terminal):

   ```bash
   load-tests/scripts/monitor.sh load-tests/results/monitor-blackfriday
   ```

2. **Smoke** primeiro, sempre:

   ```bash
   BASE_URL=https://orderslab.novaeslucas.tech load-tests/run.sh smoke
   ```

3. **Black Friday**:

   ```bash
   BASE_URL=https://orderslab.novaeslucas.tech load-tests/run.sh blackfriday -e PEAK=50 -e VISITORS=200 -e BROWSER=1
   ```

   No PowerShell:

   ```powershell
   .\load-tests\run.ps1 blackfriday -BaseUrl https://orderslab.novaeslucas.tech -Extra '-e','PEAK=50','-e','BROWSER=1'
   ```

Cada execução cria `load-tests/results/<perfil>-<data>/` com `relatorio.html` (dashboard do k6 exportado,
com gráficos ao longo do tempo), `resumo.json` e `k6.log`. A pasta `results/` não é versionada.

Durante a execução, o dashboard ao vivo do k6 fica em http://localhost:5665.

### Variáveis (`-e NOME=valor`)

| Variável | Padrão | Efeito |
|---|---|---|
| `PROFILE` | `smoke` | Perfil (o `run.sh` recebe como 1º argumento) |
| `BASE_URL` | `http://localhost:3000` | Alvo (o nginx do frontend) |
| `PEAK` / `VISITORS` | por perfil | Escala do pico |
| `TRACK_RATIO` | `0.3` | Fração dos compradores que ficam na tela de acompanhamento (o polling dela é a parte mais pesada) |
| `TRACK_TIMEOUT_MS` | `30000` | Quando o comprador desiste de esperar (o frontend para após ~30 s sem mudança) |
| `PROBE_TIMEOUT_MS` | `120000` | Quanto a sonda espera |
| `BROWSER` / `BROWSER_VUS` | `0` / `1` | Liga o cenário de navegador real |
| `MAX_VUS` | `2000` | Teto de VUs dos compradores |
| `PAYMENT_LIMIT` / `INVOICE_LIMIT` | `1000` / `500` | Regras de valor (se mudarem nos serviços) |

### Métricas no Grafana (opcional)

Com a stack de observabilidade de pé (`infra/docker-compose.yml`: Prometheus com receptor OTLP), as métricas do
k6 podem ir para o mesmo Prometheus que recebe as dos serviços:

```bash
PROMETHEUS_URL=http://localhost:9090 BASE_URL=http://localhost:3000 load-tests/run.sh blackfriday
```

Elas aparecem com prefixo `k6_`. No Explore do Grafana dá para colocar lado a lado a latência do k6, as métricas
HTTP/JVM/Hikari/consumidor Kafka que os serviços exportam via OTLP (procure por `http_server_requests`,
`hikaricp_connections`, `jvm_memory`, `kafka_consumer`) e os traces no Jaeger. O Prometheus precisa estar
acessível a partir da máquina do k6.

## O que olhar no resultado

| Camada | Onde | Sinal de problema |
|---|---|---|
| Front (nginx + SPA) | `http_req_duration{group:::carregar SPA}`, Web Vitals (`browser_web_vital_*`), `containers.csv` (frontend-react) | p95 da SPA subindo; LCP > 2,5 s; 502/504 (o nginx corta o upstream em 3 s) |
| Back (APIs) | `http_req_duration{name:...}` por rota, `http_req_failed`, CPU/memória em `containers.csv` | `POST /api/orders` p95 > 500 ms; erros 5xx |
| Kafka | `monitor.csv`: `lag_payment_api`, `lag_invoice_api`, `dlt_*`; Kafbat (http://localhost:8090) | Lag crescendo sem parar durante o pico; qualquer mensagem em DLT |
| Outbox | `monitor.csv`: `outbox_order`, `outbox_payment`, `outbox_invoice` | Backlog crescendo: o relay não acompanha a taxa de pedidos |
| Banco | `monitor.csv`: `pg_active`, `pg_lock_waits`; CPU do `postgres-api` | Conexões ativas no teto do pool; esperas por lock |
| Ponta a ponta | `fluxo_ponta_a_ponta{scenario:sonda}`, `fluxo_sem_conclusao`, `fluxo_desfecho_inesperado` | p95 > 10 s; fluxos sem conclusão; desfecho diferente das regras |

`fluxo_ponta_a_ponta` e `fluxo_ate_pagamento` têm a resolução do polling (1 s na sonda, 2 s nos compradores),
igual ao que o usuário vê na tela.

### Gargalos que o código sugere (hipóteses para o teste confirmar)

1. ~~**Listas sem paginação no polling.**~~ **Confirmado no `load` de 2026-10-18 e corrigido pela
   [spec 021](../specs/021-filtro-polling-por-pedido/spec.md).** A tela de acompanhamento e a aba Listagens baixavam
   `GET /api/payments` e `GET /api/invoices` **inteiros** a cada 2 s, então o custo de cada polling crescia com o
   número de pedidos. Agora o acompanhamento usa `?orderId=` e as listagens usam `?limit=200` (total em
   `X-Total-Count`), e o script simula isso. `lista_pagamentos_kb` e `lista_notas_kb` devem ficar estáveis; se
   voltarem a crescer, é regressão.
2. **Teto de vazão do outbox.** Cada `OutboxRelay` publica no máximo 100 eventos por ciclo (`batch-size=100`),
   esperando o ack de cada um, com 1 s de pausa entre ciclos (`fixedDelay`). Isso dá menos de ~100 eventos/s por
   serviço: acima disso o backlog do outbox cresce e o fluxo ponta a ponta atrasa, mesmo com o `POST` rápido.
3. **Timeout de 3 s no nginx** (`proxy_read_timeout`): requisição lenta vira 504 para o usuário.
4. **Pool padrão do Hikari** (10 conexões por serviço) e `spring.jpa.show-sql=true` (log de todo SQL) sob carga.
5. **Uma instância só de cada coisa** (1 broker Kafka, 1 Postgres compartilhado pelos 3 bancos, 1 réplica de cada
   API, 3 partições por tópico).

## Cuidados ao rodar contra produção

- Rode o `smoke` antes e suba aos poucos (`load` → `blackfriday`).
- Há um freio de segurança: se mais de 50% das requisições falharem por 1 min, o k6 aborta sozinho.
- A sua conexão de internet pode virar o gargalo antes do servidor (o polling das listas baixa muitos MB/s). Se o
  `data_received` encostar na sua banda, rode o k6 de uma máquina perto do servidor.
- Os pedidos de teste ficam no banco com `customerId` = `k6-<run>-...`. Para limpar (na máquina do docker compose):

  ```bash
  load-tests/scripts/cleanup.sh                      # só conta
  load-tests/scripts/cleanup.sh k6-blackfriday-20261018 --yes
  ```

  Por padrão o script só conta. Com `--yes` ele apaga os pedidos e os pagamentos e notas ligados a eles.
