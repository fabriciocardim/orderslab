# Quickstart: Decisão de Ownership de Schema/DB

Como validar o provisionamento, sem precisar subir os 3 microsserviços.

## Pré-requisitos

Docker rodando (`docker info` sem erro).

## Passos

```bash
cd infra
docker compose up -d postgres-api
docker compose logs postgres-api | tail -30   # confirma que o init script rodou sem erro
```

## Verificação: bancos e usuários existem

```bash
docker compose exec postgres-api psql -U apis_admin -d postgres -c "\l" | grep -E "order_db|payment_db|invoice_db"
docker compose exec postgres-api psql -U apis_admin -d postgres -c "\du" | grep -E "order_user|payment_user|invoice_user"
```

## Verificação: isolamento entre serviços

```bash
# order_user consegue conectar no próprio banco
docker compose exec postgres-api psql -U order_user -d order_db -c "SELECT current_database();"

# order_user NÃO consegue conectar no banco de outro serviço
docker compose exec postgres-api psql -U order_user -d payment_db -c "SELECT 1;"
# esperado: erro de permissão (FATAL: permission denied for database "payment_db")
```

Repetir a checagem de isolamento para `payment_user`/`invoice_db` e `invoice_user`/
`order_db`.

## Encerrar

```bash
docker compose down
```

(sem `-v` — não há volume nomeado; os dados já são efêmeros por padrão nesta fase do
laboratório.)
