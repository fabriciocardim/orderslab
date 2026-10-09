import type { ReactNode } from 'react';
import { useServiceList, LIST_LIMIT } from '../hooks/useServiceList';
import { Unavailable } from './Unavailable';

export interface Column<T> {
  header: string;
  cell: (row: T) => ReactNode;
}

interface Props<T> {
  title: string;
  service: string;
  name: string;
  fetcher: () => Promise<T[]>;
  columns: Column<T>[];
  rowKey: (row: T) => string;
}

/** Tabela de um serviço com degradação própria: indisponível não esconde as demais listas. */
export function ServiceTable<T extends { createdAt: string }>({ title, service, name, fetcher, columns, rowKey }: Props<T>) {
  const { rows, total, truncated, loading, unavailable } = useServiceList(name, fetcher);
  return (
    <section aria-label={title}>
      <h3>{title}</h3>
      {loading && <p role="status">Carregando…</p>}
      {unavailable && <Unavailable what={service} lastKnown={Boolean(rows)} />}
      {rows && rows.length === 0 && !unavailable && <p className="hint">Nenhum registro ainda.</p>}
      {rows && rows.length > 0 && (
        <>
          <table>
            <thead><tr>{columns.map((c) => <th key={c.header}>{c.header}</th>)}</tr></thead>
            <tbody>
              {rows.map((row) => (
                <tr key={rowKey(row)}>{columns.map((c) => <td key={c.header}>{c.cell(row)}</td>)}</tr>
              ))}
            </tbody>
          </table>
          {truncated && <p className="hint">Lista limitada aos {LIST_LIMIT} registros mais recentes (de {total}).</p>}
        </>
      )}
    </section>
  );
}
