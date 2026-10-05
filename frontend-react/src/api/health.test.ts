import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { getHealth } from './health';

describe('getHealth', () => {
  it('200 com status UP é UP', async () => {
    server.use(http.get('*/health/order', () => HttpResponse.json({ status: 'UP', groups: ['liveness'] })));
    expect(await getHealth('order')).toBe('UP');
  });

  it('503 com {"status":"DOWN"} é DOWN', async () => {
    server.use(http.get('*/health/payment', () => HttpResponse.json({ status: 'DOWN' }, { status: 503 })));
    expect(await getHealth('payment')).toBe('DOWN');
  });

  it('502/504 do proxy, rede e corpo inesperado são DOWN (e nunca lançam)', async () => {
    server.use(
      http.get('*/health/order', () => new HttpResponse(null, { status: 502 })),
      http.get('*/health/payment', () => HttpResponse.error()),
      http.get('*/health/invoice', () => HttpResponse.json({ outra: 'coisa' })),
    );
    expect(await getHealth('order')).toBe('DOWN');
    expect(await getHealth('payment')).toBe('DOWN');
    expect(await getHealth('invoice')).toBe('DOWN');
  });
});
