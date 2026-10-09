# Quickstart: Persistência JPA Simétrica

Como validar que os dados realmente persistem entre restarts.

## Pré-requisitos

```bash
cd infra && docker compose up -d postgres-api
```

(já provisiona os 3 bancos/usuários dedicados — ver item 1.9).

## Passo a passo (exemplo com order-api, mesmo padrão para os outros 2)

```bash
cd order-api && ./mvnw spring-boot:run &
```

Aguarde subir, depois:

```bash
# criar um pedido
ID=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-1","amount":100}' | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "ID=$ID"

# confirmar que existe
curl -s http://localhost:8080/api/orders/$ID
```

Agora **derrube e suba o processo de novo** (simulando um restart):

```bash
# Ctrl+C no processo, depois:
cd order-api && ./mvnw spring-boot:run &
```

```bash
# o mesmo pedido continua existindo, com os mesmos dados
curl -s http://localhost:8080/api/orders/$ID
```

## Verificação direta no banco (opcional)

```bash
docker compose -f infra/docker-compose.yml exec postgres-api \
  psql -U order_user -d order_db -c "SELECT id, customer_id, status FROM orders;"
```

## Verificação: schema criado pela migração, não pelo Hibernate

```bash
docker compose -f infra/docker-compose.yml exec postgres-api \
  psql -U order_user -d order_db -c "SELECT version, description FROM flyway_schema_history;"
```

Deve listar a migração `V1__create_orders_table` aplicada.
