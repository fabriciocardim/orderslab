#!/usr/bin/env bash
# Coleta, durante o teste, o que o k6 não enxerga de fora: lag dos consumidores no Kafka, backlog do outbox,
# DLTs, conexões/locks do Postgres e CPU/memória de cada contêiner.
#
# Roda NA MÁQUINA onde está o docker compose do orderslab (usa docker exec / docker stats).
#   load-tests/scripts/monitor.sh [pasta-de-saída]        # Ctrl+C para parar
#
# Saída: <pasta>/monitor.csv (uma linha por amostra) e <pasta>/containers.csv (docker stats).
# Variáveis: INTERVAL (s, padrão 10), KAFKA_CONTAINER, PG_CONTAINER, PG_USER, CONTAINERS (lista do docker stats).
set -uo pipefail

OUT="${1:-load-tests/results/monitor-$(date +%Y%m%d-%H%M%S)}"
INTERVAL="${INTERVAL:-10}"
KAFKA="${KAFKA_CONTAINER:-kafka-orderslab}"
PG="${PG_CONTAINER:-postgres-api}"
PG_USER="${PG_USER:-apis_admin}"
CONTAINERS="${CONTAINERS:-frontend-react order-api payment-api invoice-api kafka-orderslab postgres-api}"
KBIN=/opt/kafka/bin

mkdir -p "$OUT"
MON="$OUT/monitor.csv"
STATS="$OUT/containers.csv"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"; echo; echo "amostras em $MON e $STATS"; exit 0' INT TERM

for c in "$KAFKA" "$PG"; do
  docker inspect "$c" >/dev/null 2>&1 || { echo "contêiner $c não encontrado (ajuste KAFKA_CONTAINER/PG_CONTAINER)"; exit 1; }
done

echo "time,lag_payment_api,lag_invoice_api,order_created_total,payment_reserved_total,invoice_issued_total,dlt_order_created,dlt_payment_reserved,outbox_order,outbox_payment,outbox_invoice,orders,payments,invoices,pg_active,pg_total,pg_lock_waits" >"$MON"
echo "time,container,cpu_pct,mem_usage,net_io,block_io" >"$STATS"

psql_at() { docker exec "$PG" psql -U "$PG_USER" -d "$1" -Atc "$2" 2>/dev/null; }

printf '%-8s %8s %8s %8s %8s %8s %8s %6s %6s\n' hora lag_pay lag_inv outbox_o outbox_p outbox_i pedidos pg_on locks
while true; do
  now="$(date +%H:%M:%S)"

  # as ferramentas do Kafka sobem uma JVM cada: roda em paralelo com o resto
  docker exec "$KAFKA" "$KBIN/kafka-consumer-groups.sh" --bootstrap-server localhost:9092 --describe --all-groups \
    >"$TMP/groups" 2>/dev/null &
  docker exec "$KAFKA" "$KBIN/kafka-get-offsets.sh" --bootstrap-server localhost:9092 --topic '.*' \
    >"$TMP/offsets" 2>/dev/null &
  docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}' $CONTAINERS \
    >"$TMP/stats" 2>/dev/null &

  o="$(psql_at order_db "select (select count(*) from outbox_events), (select count(*) from orders)")"
  p="$(psql_at payment_db "select (select count(*) from outbox_events), (select count(*) from payments)")"
  i="$(psql_at invoice_db "select (select count(*) from outbox_events), (select count(*) from invoices)")"
  conn="$(psql_at postgres "select count(*) filter (where state = 'active'), count(*), count(*) filter (where wait_event_type = 'Lock') from pg_stat_activity where datname like '%\_db'")"
  wait

  # LAG é a 6ª coluna (GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG ...)
  lag() { awk -v g="$1" '$1 == g && $6 ~ /^[0-9]+$/ { s += $6 } END { print s + 0 }' "$TMP/groups"; }
  # kafka-get-offsets imprime tópico:partição:offset-final
  end() { awk -F: -v t="$1" '$1 == t { s += $3 } END { print s + 0 }' "$TMP/offsets"; }

  lp="$(lag payment-api)"; li="$(lag invoice-api)"
  IFS='|' read -r oo on <<<"${o:-|}"; IFS='|' read -r po pn <<<"${p:-|}"; IFS='|' read -r io in <<<"${i:-|}"
  IFS='|' read -r pa pt pl <<<"${conn:-||}"

  echo "$now,$lp,$li,$(end order.created),$(end payment.reserved),$(end invoice.issued),$(end order.created.dlt),$(end payment.reserved.dlt),$oo,$po,$io,$on,$pn,$in,$pa,$pt,$pl" >>"$MON"
  sed "s/^/$now,/" "$TMP/stats" >>"$STATS"
  printf '%-8s %8s %8s %8s %8s %8s %8s %6s %6s\n' "$now" "$lp" "$li" "$oo" "$po" "$io" "$on" "$pa" "$pl"

  sleep "$INTERVAL"
done
