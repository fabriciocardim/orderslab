// Perfis de carga. Cada perfil descreve, em estágios, a taxa de pedidos (compradores, em pedidos/s),
// o número de visitantes navegando na UI e quanto tempo a sonda continua medindo depois do fim da
// carga (dreno: tempo para Kafka/outbox esvaziarem o backlog).
//
// PEAK (pedidos/s no pico) e VISITORS (visitantes simultâneos no pico) escalam os perfis via env.

function num(name, fallback) {
  const raw = __ENV[name];
  return raw === undefined || raw === '' ? fallback : Number(raw);
}

const r = (n) => Math.max(0, Math.round(n));

function sum(stages) {
  return stages.reduce((total, s) => total + seconds(s.duration), 0);
}

export function seconds(duration) {
  const match = /^(\d+)(s|m|h)$/.exec(duration);
  if (!match) throw new Error(`duração inválida: ${duration}`);
  return Number(match[1]) * { s: 1, m: 60, h: 3600 }[match[2]];
}

const PROFILES = {
  // sanidade: 1 pedido/s por 1 min; confere que tudo responde antes de qualquer teste maior
  smoke: (P = num('PEAK', 1), V = num('VISITORS', 2)) => ({
    buyers: [{ target: P, duration: '10s' }, { target: P, duration: '50s' }],
    visitors: [{ target: V, duration: '10s' }, { target: V, duration: '50s' }],
    drain: '30s',
  }),

  // dia comum: rampa até a carga normal e fica 10 min
  load: (P = num('PEAK', 10), V = num('VISITORS', 50)) => ({
    buyers: [
      { target: P, duration: '2m' },
      { target: P, duration: '10m' },
      { target: 0, duration: '1m' },
    ],
    visitors: [
      { target: V, duration: '2m' },
      { target: V, duration: '10m' },
      { target: 0, duration: '1m' },
    ],
    drain: '2m',
  }),

  // Black Friday: esquenta antes da meia-noite, abre as ofertas (rampa de 1 min até o pico),
  // pico de 5 min, cai para metade e sustenta, esfria. ~19 min de carga + dreno.
  blackfriday: (P = num('PEAK', 50), V = num('VISITORS', 200)) => ({
    buyers: [
      { target: r(P * 0.1), duration: '2m' }, // esquenta: gente entrando para ver as ofertas
      { target: r(P * 0.1), duration: '2m' },
      { target: P, duration: '1m' }, // meia-noite: abertura das ofertas
      { target: P, duration: '5m' }, // pico
      { target: r(P * 0.5), duration: '2m' },
      { target: r(P * 0.5), duration: '5m' }, // madrugada ainda movimentada
      { target: 0, duration: '2m' },
    ],
    visitors: [
      { target: r(V * 0.2), duration: '2m' },
      { target: r(V * 0.2), duration: '2m' },
      { target: V, duration: '1m' },
      { target: V, duration: '5m' },
      { target: r(V * 0.5), duration: '2m' },
      { target: r(V * 0.5), duration: '5m' },
      { target: 0, duration: '2m' },
    ],
    drain: '5m',
  }),

  // pico repentino (ex.: oferta relâmpago anunciada na TV): de 10% para 2x o pico em 10 s
  spike: (P = num('PEAK', 50), V = num('VISITORS', 200)) => ({
    buyers: [
      { target: r(P * 0.1), duration: '2m' },
      { target: P * 2, duration: '10s' },
      { target: P * 2, duration: '3m' },
      { target: r(P * 0.1), duration: '10s' },
      { target: r(P * 0.1), duration: '5m' }, // a fila drena enquanto a carga volta ao normal?
    ],
    visitors: [
      { target: r(V * 0.2), duration: '2m' },
      { target: V * 2, duration: '10s' },
      { target: V * 2, duration: '3m' },
      { target: r(V * 0.2), duration: '10s' },
      { target: r(V * 0.2), duration: '5m' },
    ],
    drain: '5m',
  }),

  // estresse: degraus crescentes até achar o ponto de ruptura (PEAK = último degrau)
  stress: (P = num('PEAK', 200), V = num('VISITORS', 50)) => {
    const steps = [0.05, 0.125, 0.25, 0.5, 0.75, 1].map((f) => r(P * f));
    const buyers = steps.flatMap((target) => [
      { target, duration: '30s' },
      { target, duration: '2m' },
    ]);
    buyers.push({ target: 0, duration: '1m' });
    return {
      buyers,
      visitors: [{ target: V, duration: '1m' }, { target: V, duration: `${sum(buyers) - 60}s` }],
      drain: '5m',
    };
  },

  // resistência: carga moderada por 1 h (vazamento de memória, crescimento das listas, disco)
  soak: (P = num('PEAK', 20), V = num('VISITORS', 50)) => ({
    buyers: [{ target: P, duration: '5m' }, { target: P, duration: '60m' }, { target: 0, duration: '2m' }],
    visitors: [{ target: V, duration: '5m' }, { target: V, duration: '60m' }, { target: 0, duration: '2m' }],
    drain: '5m',
  }),
};

export function loadProfile(name) {
  const factory = PROFILES[name];
  if (!factory) throw new Error(`perfil desconhecido: ${name} (use: ${Object.keys(PROFILES).join(', ')})`);
  const profile = factory();
  const loadSeconds = Math.max(sum(profile.buyers), sum(profile.visitors));
  const peakRate = Math.max(...profile.buyers.map((s) => s.target));
  const peakVisitors = Math.max(...profile.visitors.map((s) => s.target));
  return { name, ...profile, loadSeconds, totalSeconds: loadSeconds + seconds(profile.drain), peakRate, peakVisitors };
}
