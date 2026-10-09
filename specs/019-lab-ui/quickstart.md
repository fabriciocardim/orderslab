# Quickstart: rodar e validar a UI do laboratório

## Pré-requisitos

Node ≥ 22.12 e npm; Docker. Para a validação com a stack real: os três serviços (via `spring-boot:run`, um terminal cada)
e Kafka/Postgres do Compose. **Máquina de 8 GB**: não suba os três serviços em contêineres ao mesmo tempo.

```bash
docker compose -f infra/docker-compose.yml up -d postgres-api kafka kafbat-ui
(cd order-api   && SERVER_PORT=8081 ./mvnw spring-boot:run)   # terminais separados
(cd payment-api && SERVER_PORT=8082 ./mvnw spring-boot:run)
(cd invoice-api && SERVER_PORT=8083 ./mvnw spring-boot:run)
```

## Desenvolvimento

```bash
cd frontend-react && npm ci && npm run dev      # http://localhost:5173, proxy do Vite → 8081/8082/8083
npm run lint && npm test && npm run build       # gates locais
```

## Cenários (cada botão da tela cria um pedido)

| Cenário | Valor | Esperado (pedido → pagamento → nota) |
|---|---|---|
| Baixo | 10.50 | feito → feito → feito |
| Exatamente 500 | 500 | feito → feito → feito |
| Entre 500 e 1000 | 750 | feito → feito → **falhou** |
| Exatamente 1000 | 1000 | feito → feito → **falhou** (1000 ≤ limite do pagamento, > 500) |
| Acima de 1000 | 1500 | feito → **falhou** → não se aplica |

## Falhas e degradação

Parar um serviço (`Ctrl+C`): só o painel dele vira "indisponível" (com o último dado), o resto segue; o polling para. Parar o
Postgres: o health do serviço vira "fora do ar". Criar pedido com cliente vazio/valor ≤ 0: erros de validação no formulário.

## Imagem nginx

```bash
docker build -t frontend-react frontend-react
docker run --rm -p 3000:80 -e ORDER_API_URL=http://host.docker.internal:8081 \
  -e PAYMENT_API_URL=http://host.docker.internal:8082 -e INVOICE_API_URL=http://host.docker.internal:8083 \
  -e KAFBAT_URL=http://localhost:8090 frontend-react
curl -s localhost:3000/config.js ; curl -s localhost:3000/health/order ; curl -s localhost:3000/api/orders | head -c 200
```

Conferir: `config.js` com a URL, `/health/order` → `{"status":"UP",...}`, `/api/orders` → lista, log JSON no `docker logs`.

## Compose / k8s

`docker compose -f infra/docker-compose.yml up -d frontend` (porta 3000); k8s: `kubectl apply -f infra/k8s/frontend-*.yaml` e
port-forward do `frontend` e do `kafbat-ui`. Validar com `docker compose config` e `kubectl apply --dry-run=client` se disponível.

## Links do Kafbat

No cartão de tópicos, cada link abre `…/ui/clusters/orderslab/all-topics/<tópico>` no Kafbat; conferir os 9 (7 tópicos + 2 DLTs)
com o Kafka do Compose no ar.
