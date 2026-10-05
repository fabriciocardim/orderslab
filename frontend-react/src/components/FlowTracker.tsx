import { useState } from 'react';
import { kafbatTopicUrl } from '../config';
import { cancelOrder, confirmOrder } from '../api/orders';
import { ApiError } from '../domain/types';
import type { StepState } from '../domain/types';
import { useTrackedOrder } from '../hooks/useTrackedOrder';
import { Unavailable } from './Unavailable';

interface Props {
  orderId: string | null;
}

const LABEL: Record<StepState, string> = {
  done: 'Feito',
  failed: 'Falhou',
  waiting: 'Aguardando',
  not_applicable: 'Não se aplica',
  cancelled: 'Cancelado',
};

function KafbatLink({ topic }: { topic: string }) {
  return (
    <a href={kafbatTopicUrl(topic, 'messages')} target="_blank" rel="noreferrer">
      Ver evento no Kafbat ({topic})
    </a>
  );
}

export function FlowTracker({ orderId }: Props) {
  const { snapshot, flow, loading, stopped, refresh } = useTrackedOrder(orderId);
  const [actionError, setActionError] = useState<string | null>(null);

  if (orderId === null) return <p className="hint">Crie um pedido (ou escolha um cenário) para acompanhar o fluxo.</p>;
  if (loading) return <p role="status">Carregando o pedido…</p>;
  if (snapshot?.notFound) return <p className="error" role="alert">Pedido não encontrado.</p>;

  const order = snapshot?.order;
  const sameId = (id: string) => id.toLowerCase() === orderId.toLowerCase();
  const latest = <T extends { orderId: string; createdAt: string }>(items: T[]) =>
    items.filter((item) => sameId(item.orderId)).sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
  const payment = latest(snapshot?.payments ?? []);
  const invoice = latest(snapshot?.invoices ?? []);
  const panels = snapshot?.panels ?? { order: 'ok', payment: 'ok', invoice: 'ok' };

  async function act(action: 'confirm' | 'cancel') {
    setActionError(null);
    try {
      await (action === 'confirm' ? confirmOrder(orderId as string) : cancelOrder(orderId as string));
      refresh();
    } catch (error) {
      setActionError(
        error instanceof ApiError && error.status === 409
          ? 'O pedido não está em um estado que permita essa ação.'
          : error instanceof ApiError && error.status === 404
            ? 'Pedido não encontrado.'
            : 'Não foi possível executar a ação (serviço indisponível?).',
      );
    }
  }

  return (
    <section aria-label="Acompanhamento do pedido">
      <h3>Pedido {orderId.slice(0, 8)}…</h3>
      <ol className="steps">
        <li data-testid="step-order" data-state={flow.order}>
          <strong>Pedido</strong> <span className="badge">{LABEL[flow.order]}</span>
          {order && <small> {order.status} · {order.amount.toFixed(2)}</small>}
          {panels.order === 'unavailable' && <Unavailable what="order-api" lastKnown={Boolean(order)} />}
        </li>
        <li data-testid="step-payment" data-state={flow.payment}>
          <strong>Pagamento</strong> <span className="badge">{LABEL[flow.payment]}</span>
          {payment && <small> {payment.status}</small>}
          {flow.payment === 'failed' && <div><KafbatLink topic="payment.failed" /></div>}
          {panels.payment === 'unavailable' && <Unavailable what="payment-api" lastKnown={Boolean(payment)} />}
        </li>
        <li data-testid="step-invoice" data-state={flow.invoice}>
          <strong>Nota</strong> <span className="badge">{LABEL[flow.invoice]}</span>
          {invoice && <small> {invoice.status}</small>}
          {flow.invoice === 'failed' && <div><KafbatLink topic="invoice.failed" /></div>}
          {panels.invoice === 'unavailable' && <Unavailable what="invoice-api" lastKnown={Boolean(invoice)} />}
        </li>
      </ol>

      {stopped && (
        <p className="hint" role="status">
          Continua aguardando — atualização automática pausada.{' '}
          <button type="button" onClick={refresh}>Atualizar agora</button>
        </p>
      )}

      {order?.status === 'PENDING' && (
        <div className="actions">
          <button type="button" onClick={() => void act('confirm')}>Confirmar pedido</button>
          <button type="button" onClick={() => void act('cancel')}>Cancelar pedido</button>
        </div>
      )}
      {actionError && <p className="error" role="alert">{actionError}</p>}
    </section>
  );
}
