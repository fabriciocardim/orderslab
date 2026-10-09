import { useQuery } from '@tanstack/react-query';
import { POLL_INTERVAL_MS } from './useTrackedOrder';

export const LIST_LIMIT = 200;

/**
 * Lista de um serviço: atualiza a cada 2 s enquanto a aba está visível e a tela montada; mantém o último dado se uma
 * rodada falhar; ordena por createdAt (mais recente primeiro) e corta nos 200 mais recentes.
 */
export function useServiceList<T extends { createdAt: string }>(name: string, fetcher: () => Promise<T[]>) {
  const query = useQuery({
    queryKey: ['list', name],
    queryFn: fetcher,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: false,
  });
  const all = query.data ? [...query.data].sort((a, b) => b.createdAt.localeCompare(a.createdAt)) : undefined;
  return {
    rows: all?.slice(0, LIST_LIMIT),
    total: all?.length ?? 0,
    truncated: (all?.length ?? 0) > LIST_LIMIT,
    loading: query.isPending,
    unavailable: query.isError,
  };
}
