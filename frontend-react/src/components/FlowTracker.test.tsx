import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../domain/types';
import type { Invoice, Order, Payment } from '../domain/types';
import { renderWithClient } from '../test/utils';
import { FlowTracker } from './FlowTracker';

vi.mock('../api/orders', () => ({ getOrder: vi.fn(), confirmOrder: vi.fn(), cancelOrder: vi.fn() }));
vi.mock('../api/payments', () => ({ listPayments: vi.fn() }));
vi.mock('../api/invoices', () => ({ listInvoices: vi.fn() }));

import { cancelOrder, confirmOrder, getOrder } from '../api/orders';
import { listInvoices } from '../api/invoices';
import { listPayments } from '../api/payments';

const ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
const order = (status: Order['status'] = 'PENDING'): Order => ({ id: ID, customerId: 'c', amount: 10, status, createdAt: '2026-10-05T10:00:00Z', updatedAt: 't' });
const payment = (status: Payment['status']): Payment => ({ id: 'p1', orderId: ID, amount: 10, status, createdAt: '2026-10-05T10:00:01Z', updatedAt: 't' });
const invoice = (status: Invoice['status']): Invoice => ({ id: 'i1', orderId: ID, paymentId: 'p1', amount: 10, status, createdAt: '2026-10-05T10:00:02Z', updatedAt: 't' });

/** Avança o relógio falso e deixa as promessas/renderizações assentarem. */
const tick = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });
const state = (step: 'order' | 'payment' | 'invoice') => screen.getByTestId(`step-${step}`).getAttribute('data-state');

beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(getOrder).mockResolvedValue(order());
  vi.mocked(listPayments).mockResolvedValue([]);
  vi.mocked(listInvoices).mockResolvedValue([]);
});
afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('FlowTracker', () => {
  it('sem pedido escolhido pede para criar um', () => {
    renderWithClient(<FlowTracker orderId={null} />);
    expect(screen.getByText(/Crie um pedido/)).toBeInTheDocument();
  });

  it('evolui de aguardando para feito e PARA de atualizar ao chegar ao estado final', async () => {
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);
    expect([state('order'), state('payment'), state('invoice')]).toEqual(['done', 'waiting', 'waiting']);

    vi.mocked(listPayments).mockResolvedValue([payment('RESERVED')]);
    vi.mocked(listInvoices).mockResolvedValue([invoice('ISSUED')]);
    await tick(2000);
    expect([state('order'), state('payment'), state('invoice')]).toEqual(['done', 'done', 'done']);

    const calls = vi.mocked(getOrder).mock.calls.length;
    await tick(20_000);
    expect(vi.mocked(getOrder).mock.calls.length).toBe(calls); // parou: estado final
  });

  it('atualiza a cada ~2 s enquanto aguarda', async () => {
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);
    const first = vi.mocked(getOrder).mock.calls.length;
    await tick(2000);
    await tick(2000);
    expect(vi.mocked(getOrder).mock.calls.length).toBe(first + 2);
  });

  it('pagamento FAILED: nota "não se aplica" e link do Kafbat para payment.failed', async () => {
    vi.mocked(listPayments).mockResolvedValue([payment('FAILED')]);
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);

    expect([state('payment'), state('invoice')]).toEqual(['failed', 'not_applicable']);
    const link = screen.getByRole('link', { name: /payment\.failed/ });
    expect(link).toHaveAttribute('href', 'http://localhost:8090/ui/clusters/orderslab/all-topics/payment.failed/messages');
  });

  it('nota FAILED: link do Kafbat para invoice.failed', async () => {
    vi.mocked(listPayments).mockResolvedValue([payment('RESERVED')]);
    vi.mocked(listInvoices).mockResolvedValue([invoice('FAILED')]);
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);

    expect(state('invoice')).toBe('failed');
    expect(screen.getByRole('link', { name: /invoice\.failed/ })).toBeInTheDocument();
  });

  it('PARA após ~30 s sem mudança, avisa que continua aguardando e "Atualizar agora" retoma', async () => {
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);
    expect(screen.queryByText(/atualização automática pausada/)).not.toBeInTheDocument();

    await tick(31_000);
    expect(screen.getByText(/atualização automática pausada/)).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();

    const calls = vi.mocked(getOrder).mock.calls.length;
    await tick(10_000);
    expect(vi.mocked(getOrder).mock.calls.length).toBe(calls); // pausado: sem novas chamadas

    await act(async () => { screen.getByRole('button', { name: 'Atualizar agora' }).click(); await vi.advanceTimersByTimeAsync(0); });
    expect(vi.mocked(getOrder).mock.calls.length).toBeGreaterThan(calls);
    expect(screen.queryByText(/atualização automática pausada/)).not.toBeInTheDocument();
  });

  it('pedido inexistente (404) mostra "não encontrado" e para', async () => {
    vi.mocked(getOrder).mockRejectedValue(new ApiError(404, 'não encontrado'));
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);

    expect(screen.getByRole('alert')).toHaveTextContent('Pedido não encontrado.');
    const calls = vi.mocked(getOrder).mock.calls.length;
    await tick(10_000);
    expect(vi.mocked(getOrder).mock.calls.length).toBe(calls);
  });

  it('serviço de pagamentos fora do ar: só aquele painel fica indisponível, o resto segue', async () => {
    vi.mocked(listPayments).mockRejectedValue(new ApiError(503, 'x'));
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);

    expect(state('order')).toBe('done');
    expect(screen.getByText(/payment-api indisponível/)).toBeInTheDocument();
    expect(screen.queryByText(/order-api indisponível/)).not.toBeInTheDocument();
  });

  it('confirmar chama a API; 409 mostra a mensagem de estado inválido', async () => {
    vi.mocked(confirmOrder).mockResolvedValueOnce(order('CONFIRMED'));
    renderWithClient(<FlowTracker orderId={ID} />);
    await tick(0);

    await act(async () => { screen.getByRole('button', { name: 'Confirmar pedido' }).click(); await vi.advanceTimersByTimeAsync(0); });
    expect(confirmOrder).toHaveBeenCalledWith(ID);

    vi.mocked(cancelOrder).mockRejectedValueOnce(new ApiError(409, 'estado inválido'));
    vi.mocked(getOrder).mockResolvedValue(order('PENDING'));
    await act(async () => { screen.getByRole('button', { name: 'Cancelar pedido' }).click(); await vi.advanceTimersByTimeAsync(0); });
    await tick(0);
    expect(screen.getByText(/não está em um estado que permita/)).toBeInTheDocument();
  });
});

// o userEvent não é necessário aqui (cliques síncronos dentro de act); import mantido p/ evitar divergência de setup
void userEvent;
