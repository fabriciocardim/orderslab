# Equivalente ao run.sh para Windows/PowerShell.
#   .\load-tests\run.ps1 blackfriday -BaseUrl https://orderslab.novaeslucas.tech -Extra '-e','PEAK=80'
param(
  [string]$Perfil = 'smoke',
  [string]$BaseUrl = $(if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:3000' }),
  [string]$PrometheusUrl = $env:PROMETHEUS_URL,
  [string[]]$Extra = @()
)
$ErrorActionPreference = 'Stop'
$dir = $PSScriptRoot
$runId = if ($env:RUN_ID) { $env:RUN_ID } else { "$Perfil-$(Get-Date -Format yyyyMMddHHmmss)" }
$out = Join-Path $dir "results\$runId"
New-Item -ItemType Directory -Force $out | Out-Null

$outputs = @()
if ($PrometheusUrl) {
  $env:K6_OTEL_EXPORTER_PROTOCOL = 'http/protobuf'; $env:K6_OTEL_EXPORTER_TYPE = 'http'
  $env:K6_OTEL_HTTP_EXPORTER_ENDPOINT = ($PrometheusUrl -replace '^https?://', '').TrimEnd('/')
  $env:K6_OTEL_HTTP_EXPORTER_URL_PATH = '/api/v1/otlp/v1/metrics'
  $env:K6_OTEL_HTTP_EXPORTER_INSECURE = 'true'; $env:K6_OTEL_SERVICE_NAME = 'k6'; $env:K6_OTEL_METRIC_PREFIX = 'k6_'
  $outputs = @('-o', 'opentelemetry')
}

$env:K6_WEB_DASHBOARD = 'true'
$env:K6_WEB_DASHBOARD_EXPORT = Join-Path $out 'relatorio.html'
Write-Host "run $runId -> $out"
& k6 run @outputs --summary-export (Join-Path $out 'resumo.json') `
  -e "PROFILE=$Perfil" -e "BASE_URL=$BaseUrl" -e "RUN_ID=$runId" @Extra `
  (Join-Path $dir 'blackfriday.js') 2>&1 | Tee-Object -FilePath (Join-Path $out 'k6.log')
