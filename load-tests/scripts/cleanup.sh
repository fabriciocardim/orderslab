#!/usr/bin/env bash
# Remove os dados criados pelo k6 (pedidos com customerId "k6-..." e os pagamentos/notas desses pedidos).
# Os bancos são separados (order_db, payment_db, invoice_db), então os ids saem do order_db e são usados
# para apagar nos outros dois. Por padrão só CONTA; apaga apenas com --yes.
#
# Roda na máquina do docker compose:
#   load-tests/scripts/cleanup.sh                       # conta tudo que começa com k6-
#   load-tests/scripts/cleanup.sh k6-blackfriday-abc    # conta só um run
#   load-tests/scripts/cleanup.sh k6-blackfriday-abc --yes
set -euo pipefail

PREFIX="${1:-k6-}"
CONFIRM="${2:-}"
[ "$PREFIX" = "--yes" ] && { PREFIX="k6-"; CONFIRM="--yes"; }
case "$PREFIX" in k6-*) ;; *) echo "o prefixo precisa começar com k6- (recebido: $PREFIX)"; exit 1 ;; esac

PG="${PG_CONTAINER:-postgres-api}"
PG_USER="${PG_USER:-apis_admin}"
LIKE="${PREFIX//_/\\_}%"

psql_in() { docker exec -i "$PG" psql -U "$PG_USER" -d "$1" -v ON_ERROR_STOP=1 -At; }

IDS="$(docker exec "$PG" psql -U "$PG_USER" -d order_db -Atc "select id from orders where customer_id like '$LIKE'")"
COUNT="$(printf '%s' "$IDS" | grep -c . || true)"
echo "pedidos com customerId '$PREFIX*': $COUNT"
[ "$COUNT" -eq 0 ] && exit 0

# Envia os ids para uma tabela temporária e conta/apaga no banco do serviço.
run_on() { # banco tabela
  local verb="select count(*) from $2"
  [ "$CONFIRM" = "--yes" ] && verb="with d as (delete from $2 where order_id in (select id from k6_ids) returning 1) select count(*) from d"
  {
    echo "create temp table k6_ids(id varchar(255) primary key);"
    echo "copy k6_ids from stdin;"
    printf '%s\n' "$IDS"
    echo '\.'
    if [ "$CONFIRM" = "--yes" ]; then echo "$verb;"; else echo "$verb where order_id in (select id from k6_ids);"; fi
  } | psql_in "$1" | tail -1
}

echo "pagamentos: $(run_on payment_db payments)"
echo "notas:      $(run_on invoice_db invoices)"

if [ "$CONFIRM" = "--yes" ]; then
  n="$(docker exec "$PG" psql -U "$PG_USER" -d order_db -Atc "with d as (delete from orders where customer_id like '$LIKE' returning 1) select count(*) from d")"
  echo "pedidos apagados: $n"
else
  echo "(só contagem — rode de novo com --yes para apagar)"
fi
