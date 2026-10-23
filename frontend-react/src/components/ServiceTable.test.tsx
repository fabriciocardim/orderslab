import { act, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ListPage } from '../domain/types';
import { renderWithClient } from '../test/utils';
import { ServiceTable } from './ServiceTable';
import type { Column } from './ServiceTable';

interface Row { id: string; createdAt: string; status: string }
const columns: Column<Row>[] = [{ header: 'Id', cell: (r) => r.id }, { header: 'Status', cell: (r) => r.status }];
const rows = (n: number): Row[] => Array.from({ length: n }, (_, i) => ({
  id: `id-${i}`, status: 'OK', createdAt: new Date(Date.UTC(2026, 9, 5, 10, 0, i)).toISOString(),
}));
const page = (items: Row[], total = items.length): ListPage<Row> => ({ items, total });
const tick = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });
let seq = 0;
const mount = (fetcher: () => Promise<ListPage<Row>>) => renderWithClient(
  <ServiceTable<Row> title="Pedidos" service="order-api" name={`tabela-${++seq}`} fetcher={fetcher} columns={columns} rowKey={(r) => r.id} />);

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('ServiceTable', () => {
  it('serviço antigo devolvendo tudo: mostra só os 200 mais recentes (mais novo primeiro) e avisa', async () => {
    mount(() => Promise.resolve(page(rows(250))));
    await tick(0);

    const body = screen.getAllByRole('row').slice(1); // sem o cabeçalho
    expect(body).toHaveLength(200);
    expect(body[0]).toHaveTextContent('id-249'); // o mais recente primeiro
    expect(body[199]).toHaveTextContent('id-50');
    expect(screen.getByText(/limitada aos 200 registros mais recentes \(de 250\)/)).toBeInTheDocument();
  });

  it('avisa com o total informado pelo serviço quando ele já devolve só os mais recentes', async () => {
    mount(() => Promise.resolve(page(rows(200), 3600)));
    await tick(0);

    expect(screen.getAllByRole('row').slice(1)).toHaveLength(200);
    expect(screen.getByText(/limitada aos 200 registros mais recentes \(de 3600\)/)).toBeInTheDocument();
  });

  it('sem aviso quando cabem todos', async () => {
    mount(() => Promise.resolve(page(rows(3))));
    await tick(0);
    expect(screen.getAllByRole('row')).toHaveLength(4);
    expect(screen.queryByText(/limitada/)).not.toBeInTheDocument();
  });

  it('chama o fetcher sem argumentos (o contexto do react-query não pode virar ?limit=[object Object])', async () => {
    const fetcher = vi.fn<(...args: unknown[]) => Promise<ListPage<Row>>>(() => Promise.resolve(page(rows(1))));
    mount(fetcher);
    await tick(0);
    expect(fetcher).toHaveBeenCalledWith();
  });

  it('lista vazia mostra mensagem (não fica em branco)', async () => {
    mount(() => Promise.resolve(page([])));
    await tick(0);
    expect(screen.getByText('Nenhum registro ainda.')).toBeInTheDocument();
  });

  it('serviço cai depois de uma resposta boa: "indisponível" mantendo o último dado', async () => {
    const fetcher = vi.fn<() => Promise<ListPage<Row>>>().mockResolvedValueOnce(page(rows(2))).mockRejectedValue(new Error('503'));
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
