import type { Flow, Invoice, Order, Payment, StepState } from './types';

const sameId = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();

/** O registro mais recente (por createdAt) do pedido, ou undefined. */
function latestFor<T extends { orderId: string; createdAt: string }>(items: T[], orderId: string): T | undefined {
  return items
    .filter((item) => sameId(item.orderId, orderId))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
}

function paymentStep(order: Order, payment: Payment | undefined): StepState {
  if (payment) {
    switch (payment.status) {
      case 'RESERVED':
      case 'CONFIRMED':
        return 'done';
      case 'FAILED':
        return 'failed';
      case 'CANCELLED':
        return 'cancelled';
    }
  }
  // sem pagamento: se o pedido foi cancelado, não há o que esperar
  return order.status === 'CANCELLED' ? 'not_applicable' : 'waiting';
}

function invoiceStep(payment: StepState, invoice: Invoice | undefined): StepState {
  if (invoice) {
    switch (invoice.status) {
      case 'ISSUED':
        return 'done';
      case 'FAILED':
        return 'failed';
      case 'CANCELLED':
        return 'cancelled';
      case 'PENDING':
        return 'waiting';
    }
  }
  // sem nota: só se espera uma nota quando o pagamento foi concluído (ou ainda está sendo esperado)
  return payment === 'done' || payment === 'waiting' ? 'waiting' : 'not_applicable';
}

/**
 * Deriva o estado das três etapas a partir do que cada serviço devolve. Pura e determinística.
 *
 * - pedido: feito (ou cancelado); aguardando enquanto ainda não foi carregado;
 * - pagamento: RESERVED/CONFIRMED = feito, FAILED = falhou, CANCELLED = cancelado; ausente = aguardando
 *   (ou não se aplica se o pedido foi cancelado);
 * - nota: ISSUED = feito, FAILED = falhou, CANCELLED = cancelado, PENDING = aguardando; ausente = aguardando se o
 *   pagamento foi concluído/está sendo esperado, senão não se aplica;
 * - final = nenhuma etapa aguardando.
 */
export function deriveFlow(order: Order | undefined, payments: Payment[], invoices: Invoice[]): Flow {
  if (!order) {
    return { order: 'waiting', payment: 'waiting', invoice: 'waiting', final: false };
  }
  const payment = paymentStep(order, latestFor(payments, order.id));
  const invoice = invoiceStep(payment, latestFor(invoices, order.id));
  const orderStep: StepState = order.status === 'CANCELLED' ? 'cancelled' : 'done';
  const final = ![orderStep, payment, invoice].includes('waiting');
  return { order: orderStep, payment, invoice, final };
}
