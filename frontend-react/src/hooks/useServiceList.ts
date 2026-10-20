import { useQuery } from '@tanstack/react-query';
import { LIST_LIMIT } from '../api/http';
import type { ListPage } from '../domain/types';
import { POLL_INTERVAL_MS } from './useTrackedOrder';

export { LIST_LIMIT };

/**
 * Lista de um serviço: atualiza a cada 2 s enquanto a aba está visível e a tela montada; mantém o último dado se uma
 * rodada falhar. O serviço já devolve só os mais recentes (até LIST_LIMIT) e o total existente; aqui só se garante a
 * ordem por createdAt (mais recente primeiro) e o corte, e "truncated" vem do total informado pelo serviço.
 */
export function useServiceList<T extends { createdAt: string }>(name: string, fetcher: () => Promise<ListPage<T>>) {
  const query = useQuery({
    queryKey: ['list', name],
    queryFn: fetcher,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: false,
  });
  const rows = query.data
    ? [...query.data.items].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, LIST_LIMIT)
    : undefined;
  const total = query.data?.total ?? 0;
  return {
    rows,
    total,
    truncated: rows !== undefined && total > rows.length,
    loading: query.isPending,
    unavailable: query.isError,
  };
}
