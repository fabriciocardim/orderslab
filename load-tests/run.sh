#!/usr/bin/env bash
# Roda um perfil e guarda tudo numa pasta própria: relatório HTML (dashboard do k6), resumo JSON e log.
#   load-tests/run.sh [perfil] [-e VAR=valor ...]
#   BASE_URL=https://orderslab.novaeslucas.tech load-tests/run.sh blackfriday -e PEAK=80
# Com PROMETHEUS_URL (ex.: http://localhost:9090) as métricas do k6 vão para o Prometheus da stack de
# observabilidade (receptor OTLP), ao lado das métricas dos serviços no Grafana.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
PROFILE="${1:-smoke}"; shift || true
BASE_URL="${BASE_URL:-http://localhost:3000}"
RUN_ID="${RUN_ID:-$PROFILE-$(date +%Y%m%d%H%M%S)}"
OUT="$DIR/results/$RUN_ID"
mkdir -p "$OUT"

OUTPUTS=()
if [ -n "${PROMETHEUS_URL:-}" ]; then
  host="${PROMETHEUS_URL#*://}"
  export K6_OTEL_EXPORTER_PROTOCOL=http/protobuf K6_OTEL_EXPORTER_TYPE=http \
    K6_OTEL_HTTP_EXPORTER_ENDPOINT="${host%/}" K6_OTEL_HTTP_EXPORTER_URL_PATH=/api/v1/otlp/v1/metrics \
    K6_OTEL_HTTP_EXPORTER_INSECURE=true K6_OTEL_SERVICE_NAME=k6 K6_OTEL_METRIC_PREFIX=k6_
  OUTPUTS+=(-o opentelemetry)
fi

echo "run $RUN_ID → $OUT"
K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT="$OUT/relatorio.html" \
  k6 run "${OUTPUTS[@]}" --summary-export "$OUT/resumo.json" \
  -e PROFILE="$PROFILE" -e BASE_URL="$BASE_URL" -e RUN_ID="$RUN_ID" "$@" \
  "$DIR/blackfriday.js" 2>&1 | tee "$OUT/k6.log"
