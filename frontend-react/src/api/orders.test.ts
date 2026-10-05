import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { ApiError } from '../domain/types';
import { cancelOrder, confirmOrder, createOrder, getOrder } from './orders';

const order = { id: 'o1', customerId: 'c', amount: 10, status: 'PENDING', createdAt: 't', updatedAt: 't' };

describe('cliente de pedidos', () => {
  it('cria pedido (201) enviando o corpo certo', async () => {
    let body: unknown;
    server.use(http.post('*/api/orders', async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(order, { status: 201 });
    }));

    const created = await createOrder({ customerId: 'cliente', amount: 10.5 });

    expect(created.id).toBe('o1');
    expect(body).toEqual({ customerId: 'cliente', amount: 10.5 });
  });

  it('400 de validação vira ApiError com as mensagens por campo', async () => {
    server.use(http.post('*/api/orders', () => HttpResponse.json(
      { status: 400, message: 'Erro de validação', path: '/api/orders', validationErrors: ['customerId: não pode estar em branco', 'amount: deve ser maior que 0'] },
      { status: 400 })));

    const error = await createOrder({ customerId: '', amount: 0 }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).validationErrors).toEqual(['customerId: não pode estar em branco', 'amount: deve ser maior que 0']);
  });

  it('404 e 409 de confirm/cancel viram ApiError com o status', async () => {
    server.use(
      http.post('*/api/orders/x/confirm', () => HttpResponse.json({ status: 404, message: 'não encontrado' }, { status: 404 })),
      http.post('*/api/orders/y/cancel', () => HttpResponse.json({ status: 409, message: 'estado inválido' }, { status: 409 })),
    );

    expect(await confirmOrder('x').catch((e: unknown) => e)).toMatchObject({ status: 404 });
    expect(await cancelOrder('y').catch((e: unknown) => e)).toMatchObject({ status: 409, message: 'estado inválido' });
  });

  it('503 do proxy sem corpo vira ApiError indisponível', async () => {
    server.use(http.get('*/api/orders/o1', () => new HttpResponse(null, { status: 503 })));

    const error = (await getOrder('o1').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(503);
    expect(error.unavailable).toBe(true);
  });

  it('falha de rede vira ApiError de status 0 (indisponível)', async () => {
    server.use(http.get('*/api/orders/o1', () => HttpResponse.error()));

    const error = (await getOrder('o1').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(0);
    expect(error.unavailable).toBe(true);
  });

  it('corpo não-JSON em 200 vira "resposta inesperada" (indisponível)', async () => {
    server.use(http.get('*/api/orders/o1', () => new HttpResponse('<html>', { status: 200 })));

    const error = (await getOrder('o1').catch((e: unknown) => e)) as ApiError;

    expect(error.unavailable).toBe(true);
  });
});
