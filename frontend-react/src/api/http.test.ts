import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { ApiError } from '../domain/types';
import { requestList } from './http';

describe('requestList', () => {
  it('devolve os itens e o total do cabeçalho X-Total-Count', async () => {
    server.use(http.get('*/api/x', () => HttpResponse.json([{ id: 1 }, { id: 2 }], { headers: { 'X-Total-Count': '3600' } })));

    expect(await requestList('/api/x')).toEqual({ items: [{ id: 1 }, { id: 2 }], total: 3600 });
  });

  it('sem o cabeçalho, o total é o tamanho da lista (serviço antigo)', async () => {
    server.use(http.get('*/api/x', () => HttpResponse.json([{ id: 1 }, { id: 2 }])));

    expect(await requestList('/api/x')).toEqual({ items: [{ id: 1 }, { id: 2 }], total: 2 });
  });

  it('cabeçalho inválido ou menor que a lista não é usado', async () => {
    server.use(http.get('*/api/x', () => HttpResponse.json([{ id: 1 }, { id: 2 }], { headers: { 'X-Total-Count': 'abc' } })));

    expect((await requestList('/api/x')).total).toBe(2);
  });

  it('corpo que não é lista vira ApiError de status 0', async () => {
    server.use(http.get('*/api/x', () => HttpResponse.json({ id: 1 })));

    const error = (await requestList('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
  });

  it('400 de validação vira ApiError com as mensagens', async () => {
    server.use(http.get('*/api/x', () => HttpResponse.json(
      { status: 400, message: 'Erro de validação', validationErrors: ['limit: deve ser menor ou igual a 500'] }, { status: 400 })));

    const error = (await requestList('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(400);
    expect(error.validationErrors).toEqual(['limit: deve ser menor ou igual a 500']);
  });
});
