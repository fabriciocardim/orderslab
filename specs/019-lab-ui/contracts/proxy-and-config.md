# Contrato: proxy do nginx, variáveis de ambiente e `config.js`

## Rotas do nginx

| Local | Destino |
|---|---|
| `/api/orders...` | `${ORDER_API_URL}` (caminho preservado) |
| `/api/payments...` | `${PAYMENT_API_URL}` (caminho preservado) |
| `/api/invoices...` | `${INVOICE_API_URL}` (caminho preservado) |
| `= /health/order` | `${ORDER_API_URL}/actuator/health` |
| `= /health/payment` | `${PAYMENT_API_URL}/actuator/health` |
| `= /health/invoice` | `${INVOICE_API_URL}/actuator/health` |
| `= /config.js` | gerado no start (sem cache) |
| `/` | SPA (`try_files $uri /index.html`) |

Regras: DNS resolvido **na requisição** (`resolver ${NGINX_LOCAL_RESOLVERS}`, `valid=10s`); `proxy_connect_timeout` e
`proxy_read_timeout` de 3 s; upstream indisponível → **502/504** sem derrubar o nginx; log de acesso em JSON
(`time`, `remote`, `method`, `uri`, `status`, `bytes`, `ms`, `upstream`) em `/dev/stdout`.

## Variáveis de ambiente do contêiner

| Variável | Padrão | Significado |
|---|---|---|
| `ORDER_API_URL` | — (obrigatória) | destino do proxy de pedidos |
| `PAYMENT_API_URL` | — (obrigatória) | destino do proxy de pagamentos |
| `INVOICE_API_URL` | — (obrigatória) | destino do proxy de notas |
| `KAFBAT_URL` | `http://localhost:8090` | base dos links do Kafbat (abre no navegador) |
| `KAFBAT_CLUSTER` | `orderslab` | nome do cluster no Kafbat |
| `NGINX_ENTRYPOINT_LOCAL_RESOLVERS` | `1` (fixada na imagem) | preenche `NGINX_LOCAL_RESOLVERS` |

Valores por ambiente: ver [research.md](../research.md), Decisão 7 (Compose `…:8080`; k8s `…:8081/8082/8083`).

## `config.js` (gerado em `/usr/share/nginx/html/config.js`)

```js
window.__LAB_CONFIG__ = { kafbatUrl: "http://localhost:8090", kafbatCluster: "orderslab" };
```

A SPA lê `window.__LAB_CONFIG__` com padrões equivalentes se ausente.

## Deep links do Kafbat

`{kafbatUrl}/ui/clusters/{kafbatCluster}/all-topics/{tópico}` e `.../{tópico}/messages` (verificado no bundle do Kafbat v1.5.0).
