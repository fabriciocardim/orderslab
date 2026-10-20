// Teste de carga de ponta a ponta do orderslab — simulação de Black Friday.
//
// Tudo passa pela mesma porta que o usuário usa (o nginx do frontend, que faz proxy para as 3 APIs),
// então a carga atravessa front → order-api → Postgres/outbox → Kafka → payment-api → Kafka → invoice-api.
//
// Cenários:
//   compradores  pedidos/s (arrival rate): abre a página, cria o pedido e — como a tela de acompanhamento —
//                faz polling a cada 2 s até o fluxo terminar (TRACK_RATIO = fração que fica na tela)
//   visitantes   VUs navegando: carregam a SPA e ficam nas abas "Listagens" (polling das 3 listas) ou "Saúde"
//   sonda        1 pedido a cada 2 s, sempre acompanhado, durante a carga E depois dela (dreno): mede quanto
//                tempo o pedido leva para virar nota mesmo quando o HTTP já respondeu rápido
//   navegador    (BROWSER=1) Chromium real criando pedido pela UI: Web Vitals + tempo até a tela mostrar o fim
//
// Uso: k6 run -e PROFILE=blackfriday -e BASE_URL=https://orderslab.novaeslucas.tech load-tests/blackfriday.js
// Detalhes, perfis e variáveis em load-tests/README.md.

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { browser } from 'k6/browser';
import { loadProfile } from './lib/profiles.js';
import { expectedOutcome, invoiceStep, latestFor, outcomeOf, paymentStep } from './lib/flow.js';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:3000').replace(/\/+$/, '');
const PROFILE = loadProfile(__ENV.PROFILE || 'smoke');
const RUN_ID = __ENV.RUN_ID || `${PROFILE.name}-${Date.now().toString(36)}`;
const TRACK_RATIO = Number(__ENV.TRACK_RATIO || 0.3);
const POLL_MS = 2000; // POLL_INTERVAL_MS do frontend
const TRACK_TIMEOUT_MS = Number(__ENV.TRACK_TIMEOUT_MS || 30000); // o frontend desiste após ~30 s sem mudança
const PROBE_TIMEOUT_MS = Number(__ENV.PROBE_TIMEOUT_MS || 120000); // a sonda espera mais: quer medir o atraso
const PAYMENT_LIMIT = Number(__ENV.PAYMENT_LIMIT || 1000);
const INVOICE_LIMIT = Number(__ENV.INVOICE_LIMIT || 500);
const BROWSER = __ENV.BROWSER === '1';
const MAX_VUS = Number(__ENV.MAX_VUS || 2000);

// ---------- métricas próprias ----------
const ordersCreated = new Counter('pedidos_criados');
const flowE2E = new Trend('fluxo_ponta_a_ponta', true); // POST do pedido → nota emitida/falha (ou pagamento falho)
const flowPayment = new Trend('fluxo_ate_pagamento', true); // POST do pedido → pagamento aparece na lista
const flowTimeout = new Rate('fluxo_sem_conclusao'); // acompanhamento desistiu antes do fim
const flowWrong = new Rate('fluxo_desfecho_inesperado'); // terminou, mas não como as regras de valor mandam
// tamanho da resposta de pagamento/nota no acompanhamento: com o filtro por pedido (spec 021) deve ficar estável;
// antes crescia a cada pedido porque a UI baixava a lista inteira
const paymentsListKB = new Trend('lista_pagamentos_kb');
const invoicesListKB = new Trend('lista_notas_kb');
const LIST_LIMIT = 200; // o mesmo que a UI pede nas listagens
const browserFlow = new Trend('navegador_fluxo_ate_tela_final', true);

// ---------- cenários ----------
const scenarios = {
  compradores: {
    executor: 'ramping-arrival-rate',
    exec: 'comprador',
    startRate: 0,
    timeUnit: '1s',
    // cada iteração leva ~3 s (página + formulário) + ~6 s de acompanhamento quando o fluxo está saudável
    preAllocatedVUs: Math.min(MAX_VUS, Math.max(5, Math.ceil(PROFILE.peakRate * (3 + TRACK_RATIO * 6)))),
    maxVUs: MAX_VUS,
    stages: PROFILE.buyers,
    gracefulStop: '35s',
  },
  visitantes: {
    executor: 'ramping-vus',
    exec: 'visitante',
    startVUs: 0,
    stages: PROFILE.visitors,
    gracefulRampDown: '10s',
  },
  sonda: {
    executor: 'constant-arrival-rate',
    exec: 'sonda',
    rate: 1,
    timeUnit: '2s',
    duration: `${PROFILE.totalSeconds}s`,
    preAllocatedVUs: 5,
    maxVUs: 120,
    gracefulStop: `${Math.ceil(PROBE_TIMEOUT_MS / 1000)}s`,
  },
};

if (BROWSER) {
  scenarios.navegador = {
    executor: 'constant-vus',
    exec: 'navegador',
    vus: Number(__ENV.BROWSER_VUS || 1),
    duration: `${PROFILE.loadSeconds}s`,
    options: { browser: { type: 'chromium' } },
  };
}

export const options = {
  scenarios,
  // um registro por rota (não por URL com UUID)
  thresholds: {
    // freio de segurança: se metade das requisições falhar por 1 min, aborta para não martelar um sistema caído
    http_req_failed: [{ threshold: 'rate<0.5', abortOnFail: true, delayAbortEval: '1m' }, 'rate<0.01'],
    'http_req_duration{name:POST /api/orders}': ['p(95)<500', 'p(99)<1500'],
    'http_req_duration{name:GET /api/orders/{id}}': ['p(95)<300'],
    'http_req_duration{name:GET /}': ['p(95)<300'],
    'http_req_duration{group:::carregar SPA}': ['p(95)<500'],
    'http_req_duration{name:GET /api/payments?orderId}': ['p(95)<300'],
    'http_req_duration{name:GET /api/invoices?orderId}': ['p(95)<300'],
    'http_req_duration{name:GET /api/payments?limit}': ['p(95)<1000'],
    'http_req_duration{name:GET /api/invoices?limit}': ['p(95)<1000'],
    // gateway do nginx corta em 3 s (proxy_read_timeout): 502/504 aparecem aqui
    'http_reqs{status:504}': ['count<1'],
    'http_reqs{status:502}': ['count<1'],
    'fluxo_ponta_a_ponta{scenario:sonda}': ['p(95)<10000'],
    fluxo_ponta_a_ponta: ['p(95)<10000'],
    fluxo_sem_conclusao: ['rate<0.01'],
    fluxo_desfecho_inesperado: ['rate<0.001'],
    checks: ['rate>0.99'],
    // pedidos que o gerador deixou de enviar porque todos os VUs estavam presos esperando o servidor
    // (ou MAX_VUS baixo demais): se passar disso, a taxa real ficou abaixo da planejada
    dropped_iterations: ['count<50'],
    ...(BROWSER
      ? {
          browser_web_vital_lcp: ['p(75)<2500'],
          browser_web_vital_cls: ['p(75)<0.1'],
          navegador_fluxo_ate_tela_final: ['p(95)<15000'],
        }
      : {}),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  tags: { run: RUN_ID, profile: PROFILE.name },
  userAgent: `orderslab-k6/${RUN_ID}`,
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// ---------- setup: aborta cedo se o alvo não estiver de pé ----------
export function setup() {
  const down = ['order', 'payment', 'invoice'].filter((svc) => {
    const res = http.get(`${BASE_URL}/health/${svc}`, { tags: { name: 'GET /health/{svc}' } });
    return res.status !== 200;
  });
  if (down.length) exec.test.abort(`serviços fora do ar antes do teste: ${down.join(', ')}`);
  console.log(
    `orderslab k6 · alvo ${BASE_URL} · perfil ${PROFILE.name} · pico ${PROFILE.peakRate} pedidos/s, ` +
      `${PROFILE.peakVisitors} visitantes · ${Math.round(PROFILE.totalSeconds / 60)} min · run ${RUN_ID}`,
  );
  return { startedAt: Date.now() };
}

// ---------- jornadas ----------

let assets; // cache por VU: os assets que o index.html referencia (o navegador também os cacheia)

/** Carrega a SPA como o navegador faria: index.html, config.js e os bundles referenciados. */
function loadSpa() {
  group('carregar SPA', () => {
    const index = http.get(`${BASE_URL}/`, { tags: { name: 'GET /' } });
    check(index, { 'index 200': (r) => r.status === 200 });
    if (assets === undefined) {
      assets = [];
      const re = /(?:src|href)="(\/assets\/[^"]+)"/g;
      let m;
      while ((m = re.exec(index.body || '')) !== null) assets.push(m[1]);
    }
    const batch = [['GET', `${BASE_URL}/config.js`, null, { tags: { name: 'GET /config.js' } }]];
    // primeira visita do VU baixa tudo; as próximas simulam cache do navegador (só index + config)
    if (exec.vu.iterationInScenario === 0) {
      for (const a of assets) batch.push(['GET', `${BASE_URL}${a}`, null, { tags: { name: 'GET /assets/*' }, responseType: 'none' }]);
    }
    const responses = http.batch(batch);
    check(responses, { 'assets 200': (rs) => rs.every((r) => r.status === 200) });
  });
}

function randomAmount() {
  // mix de carrinho: 70% até 500 (nota emitida), 20% entre 500 e 1000 (nota falha), 10% acima (pagamento falha)
  const p = Math.random();
  const cents = (min, max) => Math.round((min + Math.random() * (max - min)) * 100) / 100;
  if (p < 0.7) return cents(9.9, INVOICE_LIMIT);
  if (p < 0.9) return cents(INVOICE_LIMIT + 0.01, PAYMENT_LIMIT);
  return cents(PAYMENT_LIMIT + 0.01, PAYMENT_LIMIT * 3);
}

function createOrder() {
  const amount = randomAmount();
  const body = JSON.stringify({ customerId: `k6-${RUN_ID}-${exec.vu.idInTest}-${exec.vu.iterationInScenario}`, amount });
  const res = http.post(`${BASE_URL}/api/orders`, body, { headers: JSON_HEADERS, tags: { name: 'POST /api/orders' } });
  const ok = check(res, {
    'pedido 201': (r) => r.status === 201,
    'pedido PENDING com id': (r) => r.status === 201 && r.json('status') === 'PENDING' && Boolean(r.json('id')),
  });
  if (!ok) return null;
  ordersCreated.add(1);
  return { id: res.json('id'), amount, createdAt: Date.now() };
}

/**
 * Faz o que useTrackedOrder faz: a cada intervalo busca o pedido + os pagamentos e as notas DELE em
 * paralelo e para quando nenhuma etapa está "aguardando". Mede quanto o fluxo levou desde o POST.
 */
function track(order, intervalMs, timeoutMs) {
  const deadline = order.createdAt + timeoutMs;
  let paymentSeen = false;
  while (Date.now() < deadline) {
    sleep(intervalMs / 1000);
    const [o, p, i] = http.batch([
      ['GET', `${BASE_URL}/api/orders/${order.id}`, null, { tags: { name: 'GET /api/orders/{id}' } }],
      ['GET', `${BASE_URL}/api/payments?orderId=${order.id}`, null, { tags: { name: 'GET /api/payments?orderId' } }],
      ['GET', `${BASE_URL}/api/invoices?orderId=${order.id}`, null, { tags: { name: 'GET /api/invoices?orderId' } }],
    ]);
    check(o, { 'acompanhamento: pedido 200': (r) => r.status === 200 });
    check(p, { 'acompanhamento: pagamentos 200': (r) => r.status === 200 });
    check(i, { 'acompanhamento: notas 200': (r) => r.status === 200 });
    if (p.body) paymentsListKB.add(p.body.length / 1024);
    if (i.body) invoicesListKB.add(i.body.length / 1024);

    const orderRecord = o.status === 200 ? o.json() : undefined;
    const payment = paymentStep(orderRecord, p.status === 200 ? latestFor(p.body, order.id) : undefined);
    const invoice = invoiceStep(payment, i.status === 200 ? latestFor(i.body, order.id) : undefined);
    if (!paymentSeen && payment !== 'waiting') {
      paymentSeen = true;
      flowPayment.add(Date.now() - order.createdAt);
    }
    if (payment !== 'waiting' && invoice !== 'waiting') {
      flowE2E.add(Date.now() - order.createdAt);
      flowTimeout.add(false);
      const expected = expectedOutcome(order.amount, PAYMENT_LIMIT, INVOICE_LIMIT);
      const actual = outcomeOf(payment, invoice);
      flowWrong.add(actual !== expected, { esperado: expected, obtido: actual });
      if (actual !== expected) console.warn(`pedido ${order.id} (${order.amount}): esperado ${expected}, obtido ${actual}`);
      return;
    }
  }
  flowTimeout.add(true);
}

export function comprador() {
  loadSpa();
  sleep(1 + Math.random() * 2); // escolhe produto / preenche o formulário
  const order = createOrder();
  if (order && Math.random() < TRACK_RATIO) track(order, POLL_MS, TRACK_TIMEOUT_MS);
}

export function sonda() {
  const order = createOrder();
  if (order) track(order, 1000, PROBE_TIMEOUT_MS);
}

export function visitante() {
  loadSpa();
  const p = Math.random();
  const polls = 3 + Math.floor(Math.random() * 8); // fica 6–20 s na aba
  if (p < 0.4) {
    // aba "Listagens": as 3 tabelas fazem polling das listas (os 200 mais recentes) a cada 2 s
    for (let n = 0; n < polls; n++) {
      const rs = http.batch([
        ['GET', `${BASE_URL}/api/orders?limit=${LIST_LIMIT}`, null, { tags: { name: 'GET /api/orders?limit' }, responseType: 'none' }],
        ['GET', `${BASE_URL}/api/payments?limit=${LIST_LIMIT}`, null, { tags: { name: 'GET /api/payments?limit' }, responseType: 'none' }],
        ['GET', `${BASE_URL}/api/invoices?limit=${LIST_LIMIT}`, null, { tags: { name: 'GET /api/invoices?limit' }, responseType: 'none' }],
      ]);
      check(rs, { 'listagens 200': (all) => all.every((r) => r.status === 200) });
      sleep(POLL_MS / 1000);
    }
  } else if (p < 0.55) {
    // aba "Saúde": /health/{svc} a cada 2 s
    for (let n = 0; n < polls; n++) {
      const rs = http.batch(
        ['order', 'payment', 'invoice'].map((svc) => ['GET', `${BASE_URL}/health/${svc}`, null, { tags: { name: 'GET /health/{svc}' } }]),
      );
      check(rs, { 'saúde UP': (all) => all.every((r) => r.status === 200) });
      sleep(POLL_MS / 1000);
    }
  } else {
    sleep(3 + Math.random() * 7); // só olhou a página e saiu
  }
  sleep(1 + Math.random() * 3);
}

export async function navegador() {
  const page = await browser.newPage();
  try {
    await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle' });
    const amount = Math.round((10 + Math.random() * 400) * 100) / 100; // sempre o caminho feliz
    await page.locator('#customerId').fill(`k6-${RUN_ID}-browser-${exec.vu.idInTest}`);
    await page.locator('#amount').fill(String(amount));
    const started = Date.now();
    await page.locator('button[type="submit"]').click();
    // final = etapa da nota saiu de "aguardando" (implica pagamento também fora de "aguardando")
    await page.locator('[data-testid="step-invoice"]:not([data-state="waiting"])').waitFor({ timeout: 60000 });
    browserFlow.add(Date.now() - started);
    const state = await page.locator('[data-testid="step-invoice"]').getAttribute('data-state');
    check(state, { 'navegador: nota emitida na tela': (s) => s === 'done' });
  } finally {
    await page.close();
  }
  sleep(5);
}

export function teardown(data) {
  console.log(`fim do run ${RUN_ID} após ${Math.round((Date.now() - data.startedAt) / 1000)} s — pedidos com customerId "k6-${RUN_ID}-*"`);
}
