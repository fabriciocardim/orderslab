import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getOrder } from '../api/orders';
import { listInvoices } from '../api/invoices';
import { listPayments } from '../api/payments';
import { deriveFlow } from '../domain/flow';
import { ApiError } from '../domain/types';
import type { Flow, Invoice, Order, Payment } from '../domain/types';
import { useNoChangeTimeout } from './useNoChangeTimeout';

export const POLL_INTERVAL_MS = 2000;

export type PanelState = 'ok' | 'unavailable';

export interface FlowSnapshot {
  order?: Order;
  payments: Payment[];
  invoices: Invoice[];
  panels: { order: PanelState; payment: PanelState; invoice: PanelState };
  notFound: boolean;
}

const EMPTY: FlowSnapshot = {
  payments: [],
  invoices: [],
  panels: { order: 'ok', payment: 'ok', invoice: 'ok' },
  notFound: false,
};

/** Busca as três fontes em paralelo; cada uma que falhar mantém o último dado conhecido e marca só o seu painel. */
async function loadSnapshot(orderId: string, previous: FlowSnapshot | undefined): Promise<FlowSnapshot> {
  const before = previous ?? EMPTY;
  const [order, payments, invoices] = await Promise.allSettled([getOrder(orderId), listPayments(), listInvoices()]);
  const notFound = order.status === 'rejected' && order.reason instanceof ApiError && order.reason.status === 404;
  return {
    order: order.status === 'fulfilled' ? order.value : before.order,
    payments: payments.status === 'fulfilled' ? payments.value : before.payments,
    invoices: invoices.status === 'fulfilled' ? invoices.value : before.invoices,
    panels: {
      order: order.status === 'fulfilled' || notFound ? 'ok' : 'unavailable',
      payment: payments.status === 'fulfilled' ? 'ok' : 'unavailable',
      invoice: invoices.status === 'fulfilled' ? 'ok' : 'unavailable',
    },
    notFound,
  };
}

function fingerprintOf(snapshot: FlowSnapshot | undefined, orderId: string | null): string {
  if (!snapshot || !orderId) return 'none';
  const same = (id: string) => id.toLowerCase() === orderId.toLowerCase();
  return JSON.stringify([
    snapshot.order?.status,
    snapshot.payments.filter((p) => same(p.orderId)).map((p) => [p.id, p.status]),
    snapshot.invoices.filter((i) => same(i.orderId)).map((i) => [i.id, i.status]),
    snapshot.panels,
    snapshot.notFound,
  ]);
}

/**
 * Acompanha um pedido: atualiza a cada 2 s e **para** quando o fluxo chega a um estado final, quando o pedido não
 * existe, ou após ~30 s sem mudança (a tela mostra "continua aguardando" e oferece "Atualizar agora").
 *
 * `fingerprint` e `terminal` são estado do React atualizado pelo `queryFn` (callback assíncrono, não um efeito):
 * é o que permite ao `refetchInterval` enxergar a parada, que depende dos próprios dados da query.
 */
export function useTrackedOrder(orderId: string | null) {
  const queryClient = useQueryClient();
  const queryKey = ['flow', orderId];
  const [fingerprint, setFingerprint] = useState('none');
  const [terminal, setTerminal] = useState(false);
  const { stopped, resume } = useNoChangeTimeout(fingerprint, terminal);

  const query = useQuery({
    queryKey,
    enabled: orderId !== null,
    queryFn: async () => {
      const snapshot = await loadSnapshot(orderId as string, queryClient.getQueryData<FlowSnapshot>(queryKey));
      const flow = deriveFlow(snapshot.order, snapshot.payments, snapshot.invoices);
      setFingerprint(fingerprintOf(snapshot, orderId));
      setTerminal(snapshot.notFound || flow.final);
      return snapshot;
    },
    refetchInterval: stopped || terminal ? false : POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: false,
  });

  const snapshot = query.data;
  const flow: Flow = deriveFlow(snapshot?.order, snapshot?.payments ?? [], snapshot?.invoices ?? []);

  const refresh = () => {
    resume();
    void query.refetch();
  };

  return {
    snapshot,
    flow,
    loading: orderId !== null && query.isPending,
    stopped,
    terminal,
    refresh,
  };
}
