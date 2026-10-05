import { act, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithClient } from '../test/utils';
import { ServiceTable } from './ServiceTable';
import type { Column } from './ServiceTable';

interface Row { id: string; createdAt: string; status: string }
const columns: Column<Row>[] = [{ header: 'Id', cell: (r) => r.id }, { header: 'Status', cell: (r) => r.status }];
const rows = (n: number): Row[] => Array.from({ length: n }, (_, i) => ({
  id: `id-${i}`, status: 'OK', createdAt: new Date(Date.UTC(2026, 9, 5, 10, 0, i)).toISOString(),
}));
const tick = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });
let seq = 0;
const mount = (fetcher: () => Promise<Row[]>) => renderWithClient(
  <ServiceTable<Row> title="Pedidos" service="order-api" name={`tabela-${++seq}`} fetcher={fetcher} columns={columns} rowKey={(r) => r.id} />);

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('ServiceTable', () => {
  it('mostra só os 200 mais recentes (mais novo primeiro) e avisa que a lista foi limitada', async () => {
    mount(() => Promise.resolve(rows(250)));
    await tick(0);

    const body = screen.getAllByRole('row').slice(1); // sem o cabeçalho
    expect(body).toHaveLength(200);
    expect(body[0]).toHaveTextContent('id-249'); // o mais recente primeiro
    expect(body[199]).toHaveTextContent('id-50');
    expect(screen.getByText(/limitada aos 200 registros mais recentes \(de 250\)/)).toBeInTheDocument();
  });

  it('sem aviso quando cabem todos', async () => {
    mount(() => Promise.resolve(rows(3)));
    await tick(0);
    expect(screen.getAllByRole('row')).toHaveLength(4);
    expect(screen.queryByText(/limitada/)).not.toBeInTheDocument();
  });

  it('lista vazia mostra mensagem (não fica em branco)', async () => {
    mount(() => Promise.resolve([]));
    await tick(0);
    expect(screen.getByText('Nenhum registro ainda.')).toBeInTheDocument();
  });

  it('serviço cai depois de uma resposta boa: "indisponível" mantendo o último dado', async () => {
    const fetcher = vi.fn<() => Promise<Row[]>>().mockResolvedValueOnce(rows(2)).mockRejectedValue(new Error('503'));
    mount(fetcher);
    await tick(0);
    expect(screen.getAllByRole('row')).toHaveLength(3);

    await tick(2000);
    await tick(10); // o react-query notifica o erro com um timer curto depois do fetch
    expect(screen.getByText(/order-api indisponível — mostrando o último dado conhecido/)).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3); // o último dado segue na tela
  });

  it('serviço fora do ar desde o início: mensagem explicativa, sem tabela', async () => {
    mount(() => Promise.reject(new Error('503')));
    await tick(0);
    expect(screen.getByText(/order-api indisponível\./)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
