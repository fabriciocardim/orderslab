#!/bin/sh
# Gera o config.js de runtime (lido pela SPA em window.__LAB_CONFIG__) a partir de variáveis de ambiente.
set -eu
KAFBAT_URL="${KAFBAT_URL:-http://localhost:8090}"
KAFBAT_CLUSTER="${KAFBAT_CLUSTER:-orderslab}"
cat > /usr/share/nginx/html/config.js <<JS
window.__LAB_CONFIG__ = { kafbatUrl: "${KAFBAT_URL}", kafbatCluster: "${KAFBAT_CLUSTER}" };
JS
echo "40-config-js.sh: config.js gerado (kafbatUrl=${KAFBAT_URL}, kafbatCluster=${KAFBAT_CLUSTER})"
