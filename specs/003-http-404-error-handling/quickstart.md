# Quickstart: Tratamento de Erro HTTP — 404 Real

Como reproduzir a verificação desta feature, usando `order-api` como exemplo (os outros 2
seguem o mesmo padrão, trocando a porta e o path).

## Pré-requisitos

```bash
cd order-api && ./mvnw spring-boot:run
```

## Passos

Com o serviço no ar (porta padrão local, ver README — `order-api` roda em `8081` via
Compose, ou na porta default do Spring quando rodado direto com `mvnw spring-boot:run`):

```bash
# Um UUID que não existe
curl -i http://localhost:8080/api/orders/00000000-0000-0000-0000-000000000000
```

## Resultado esperado

- Status: `404`
- Corpo (formato, valores exatos variam):

```json
{
  "timestamp": "2026-09-24T18:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Pedido não encontrado: 00000000-0000-0000-0000-000000000000",
  "path": "/api/orders/00000000-0000-0000-0000-000000000000"
}
```

## Verificação de não-regressão

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-1","amount":100}'
```

Deve continuar retornando `201` normalmente, com o corpo de sucesso de sempre (sem
`ErrorResponse`).

## Repetir para os outros 2 serviços

- `payment-api`: `GET /api/payments/{id-inexistente}`
- `invoice-api`: `GET /api/invoices/{id-inexistente}`

Os 3 corpos de erro devem ter exatamente os mesmos nomes de campo (ver
`contracts/error-404.md`).
