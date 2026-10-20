import { http, HttpResponse } from 'msw';
import type { JsonBodyType } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { listInvoices, listInvoicesByOrder } from './invoices';
import { listOrders } from './orders';
import { listPayments, listPaymentsByOrder } from './payments';

const ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

/** Registra um GET que guarda a query string recebida e responde com `body`. */
function capture(path: string, body: JsonBodyType, headers: Record<string, string> = {}) {
  const seen: URLSearchParams[] = [];
  server.use(http.get(`*${path}`, ({ request }) => {
    seen.push(new URL(request.url).searchParams);
    return HttpResponse.json(body, { headers });
  }));
  return seen;
}

describe('consultas por pedido (tela de acompanhamento)', () => {
  it('pagamentos: pede só os do pedido via ?orderId=', async () => {
    const seen = capture('/api/payments', [{ id: 'p1', orderId: ID }]);

    const payments = await listPaymentsByOrder(ID);

    expect(seen[0].get('orderId')).toBe(ID);
    expect(seen[0].has('limit')).toBe(false);
    expect(payments).toHaveLength(1);
  });

  it('notas: pede só as do pedido via ?orderId=', async () => {
    const seen = capture('/api/invoices', []);

    expect(await listInvoicesByOrder(ID)).toEqual([]);
    expect(seen[0].get('orderId')).toBe(ID);
  });
});

describe('listagens limitadas (aba Listagens)', () => {
  it.each([
    ['/api/orders', listOrders],
    ['/api/payments', listPayments],
    ['/api/invoices', listInvoices],
  ] as const)('%s pede ?limit=200 e lê o total do cabeçalho', async (path, list) => {
    const seen = capture(path, [], { 'X-Total-Count': '42' });

    const result = await list();

    expect(seen[0].get('limit')).toBe('200');
    expect(result.total).toBe(42);
  });
});
