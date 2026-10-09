import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { SCENARIOS } from '../domain/scenarios';
import { server } from '../test/server';
import { renderWithClient } from '../test/utils';
import { NewOrderForm } from './NewOrderForm';
import { ScenarioButtons } from './ScenarioButtons';

const created = (amount = 10) => ({ id: 'novo-id', customerId: 'c', amount, status: 'PENDING', createdAt: 't', updatedAt: 't' });

describe('NewOrderForm', () => {
  it('valida localmente: cliente vazio e valor vazio/zero/negativo não chamam a API', async () => {
    const onPost = vi.fn();
    server.use(http.post('*/api/orders', () => { onPost(); return HttpResponse.json(created(), { status: 201 }); }));
    const onCreated = vi.fn();
    renderWithClient(<NewOrderForm onCreated={onCreated} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Criar pedido' }));
    expect(screen.getByText('Informe o cliente.')).toBeInTheDocument();
    expect(screen.getByText('Informe o valor.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Cliente'), 'cliente-1');
    await user.type(screen.getByLabelText('Valor'), '0');
    await user.click(screen.getByRole('button', { name: 'Criar pedido' }));
    expect(screen.getByText('O valor deve ser maior que zero.')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Valor'));
    await user.type(screen.getByLabelText('Valor'), '-5');
    await user.click(screen.getByRole('button', { name: 'Criar pedido' }));
    expect(screen.getByText('O valor deve ser maior que zero.')).toBeInTheDocument();

    expect(onPost).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('envio válido cria o pedido e avisa o id', async () => {
    let body: unknown;
    server.use(http.post('*/api/orders', async ({ request }) => { body = await request.json(); return HttpResponse.json(created(12.5), { status: 201 }); }));
    const onCreated = vi.fn();
    renderWithClient(<NewOrderForm onCreated={onCreated} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Cliente'), ' cliente-1 ');
    await user.type(screen.getByLabelText('Valor'), '12,5');
    await user.click(screen.getByRole('button', { name: 'Criar pedido' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith('novo-id'));
    expect(body).toEqual({ customerId: 'cliente-1', amount: 12.5 });
  });

  it('400 do serviço aparece nos campos do formulário', async () => {
    server.use(http.post('*/api/orders', () => HttpResponse.json(
      { status: 400, message: 'Erro de validação', validationErrors: ['amount: deve ser maior que 0'] }, { status: 400 })));
    renderWithClient(<NewOrderForm onCreated={vi.fn()} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Cliente'), 'c');
    await user.type(screen.getByLabelText('Valor'), '1');
    await user.click(screen.getByRole('button', { name: 'Criar pedido' }));

    expect(await screen.findByText('deve ser maior que 0')).toBeInTheDocument();
  });

  it('serviço indisponível mostra mensagem geral (não fica em branco)', async () => {
    server.use(http.post('*/api/orders', () => new HttpResponse(null, { status: 503 })));
    renderWithClient(<NewOrderForm onCreated={vi.fn()} />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Cliente'), 'c');
    await user.type(screen.getByLabelText('Valor'), '1');
    await user.click(screen.getByRole('button', { name: 'Criar pedido' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

describe('ScenarioButtons', () => {
  it('cada botão cria um pedido com o valor do cenário', async () => {
    const amounts: number[] = [];
    server.use(http.post('*/api/orders', async ({ request }) => {
      const body = (await request.json()) as { amount: number };
      amounts.push(body.amount);
      return HttpResponse.json(created(body.amount), { status: 201 });
    }));
    const onCreated = vi.fn();
    renderWithClient(<ScenarioButtons onCreated={onCreated} />);
    const user = userEvent.setup();

    for (const scenario of SCENARIOS) {
      await user.click(screen.getByRole('button', { name: new RegExp(scenario.label) }));
      await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(amounts.length));
    }

    expect(amounts).toEqual([10.5, 500, 750, 1000, 1500]);
    expect(onCreated).toHaveBeenCalledTimes(5);
  });

  it('falha ao criar mostra o motivo', async () => {
    server.use(http.post('*/api/orders', () => new HttpResponse(null, { status: 503 })));
    renderWithClient(<ScenarioButtons onCreated={vi.fn()} />);

    await userEvent.setup().click(screen.getByRole('button', { name: /Valor baixo/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Valor baixo');
  });
});
