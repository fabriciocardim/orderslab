import { describe, expect, it } from 'vitest';
import { deriveFlow } from './flow';
import type { Invoice, InvoiceStatus, Order, OrderStatus, Payment, PaymentStatus, StepState } from './types';

const ORDER_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

const order = (status: OrderStatus = 'PENDING'): Order => ({
  id: ORDER_ID, customerId: 'c', amount: 10, status, createdAt: '2026-10-05T10:00:00Z', updatedAt: '2026-10-05T10:00:00Z',
});
const payment = (status: PaymentStatus, createdAt = '2026-10-05T10:00:01Z', orderId = ORDER_ID): Payment => ({
  id: `p-${status}-${createdAt}`, orderId, amount: 10, status, createdAt, updatedAt: createdAt,
});
const invoice = (status: InvoiceStatus, createdAt = '2026-10-05T10:00:02Z', orderId = ORDER_ID): Invoice => ({
  id: `i-${status}-${createdAt}`, orderId, paymentId: 'p', amount: 10, status, createdAt, updatedAt: createdAt,
});

type Expected = [order: StepState, payment: StepState, invoice: StepState, final: boolean];

describe('deriveFlow', () => {
  it('pedido ainda não carregado: tudo aguardando e não final', () => {
    expect(deriveFlow(undefined, [], [])).toEqual({ order: 'waiting', payment: 'waiting', invoice: 'waiting', final: false });
  });

  // [pedido, pagamento (ou ausente), nota (ou ausente)] → [pedido, pagamento, nota, final]
  const table: Array<[string, OrderStatus, PaymentStatus | null, InvoiceStatus | null, Expected]> = [
    ['recém-criado, nada ainda', 'PENDING', null, null, ['done', 'waiting', 'waiting', false]],
    ['pagamento reservado, nota ainda não', 'PENDING', 'RESERVED', null, ['done', 'done', 'waiting', false]],
    ['pagamento confirmado, nota ainda não', 'PENDING', 'CONFIRMED', null, ['done', 'done', 'waiting', false]],
    ['cenário baixo/500/pedido→pagamento→nota emitida', 'PENDING', 'RESERVED', 'ISSUED', ['done', 'done', 'done', true]],
    ['cenário 750/1000: pagamento ok, nota falhou', 'PENDING', 'RESERVED', 'FAILED', ['done', 'done', 'failed', true]],
    ['cenário 1500: pagamento falhou, sem nota', 'PENDING', 'FAILED', null, ['done', 'failed', 'not_applicable', true]],
    ['pagamento cancelado manualmente, sem nota', 'PENDING', 'CANCELLED', null, ['done', 'cancelled', 'not_applicable', true]],
    ['nota criada manualmente ainda pendente', 'PENDING', 'RESERVED', 'PENDING', ['done', 'done', 'waiting', false]],
    ['nota cancelada manualmente', 'PENDING', 'RESERVED', 'CANCELLED', ['done', 'done', 'cancelled', true]],
    ['pedido confirmado, fluxo completo', 'CONFIRMED', 'RESERVED', 'ISSUED', ['done', 'done', 'done', true]],
    ['pedido confirmado, pagamento falhou', 'CONFIRMED', 'FAILED', null, ['done', 'failed', 'not_applicable', true]],
    ['pedido cancelado sem pagamento: nada a esperar', 'CANCELLED', null, null, ['cancelled', 'not_applicable', 'not_applicable', true]],
    ['pedido cancelado depois do pagamento reservado, nota ainda não', 'CANCELLED', 'RESERVED', null, ['cancelled', 'done', 'waiting', false]],
    ['pedido cancelado com pagamento falho', 'CANCELLED', 'FAILED', null, ['cancelled', 'failed', 'not_applicable', true]],
  ];

  it.each(table)('%s', (_name, orderStatus, paymentStatus, invoiceStatus, [o, p, i, final]) => {
    const flow = deriveFlow(
      order(orderStatus),
      paymentStatus ? [payment(paymentStatus)] : [],
      invoiceStatus ? [invoice(invoiceStatus)] : [],
    );
    expect(flow).toEqual({ order: o, payment: p, invoice: i, final });
  });

  it('nota presente com pagamento falho mostra a nota como ela é (não esconde o dado)', () => {
    expect(deriveFlow(order(), [payment('FAILED')], [invoice('ISSUED')]).invoice).toBe('done');
  });

  it('ignora pagamentos e notas de outros pedidos', () => {
    const other = '00000000-0000-0000-0000-000000000000';
    const flow = deriveFlow(order(), [payment('RESERVED', undefined, other)], [invoice('ISSUED', undefined, other)]);
    expect(flow).toEqual({ order: 'done', payment: 'waiting', invoice: 'waiting', final: false });
  });

  it('usa o registro mais recente quando há vários do mesmo pedido', () => {
    const payments = [payment('FAILED', '2026-10-05T10:00:01Z'), payment('RESERVED', '2026-10-05T10:00:09Z')];
    const invoices = [invoice('ISSUED', '2026-10-05T10:00:02Z'), invoice('FAILED', '2026-10-05T10:00:10Z')];
    expect(deriveFlow(order(), payments, invoices)).toEqual({ order: 'done', payment: 'done', invoice: 'failed', final: true });
  });

  it('compara o orderId sem diferenciar maiúsculas/minúsculas', () => {
    const flow = deriveFlow(order(), [payment('RESERVED', undefined, ORDER_ID.toUpperCase())], []);
    expect(flow.payment).toBe('done');
  });

  it('exaustivo: final ⇔ nenhuma etapa aguardando, para todas as combinações', () => {
    const orders: OrderStatus[] = ['PENDING', 'CONFIRMED', 'CANCELLED'];
    const pays: Array<PaymentStatus | null> = [null, 'RESERVED', 'CONFIRMED', 'CANCELLED', 'FAILED'];
    const invs: Array<InvoiceStatus | null> = [null, 'PENDING', 'ISSUED', 'CANCELLED', 'FAILED'];
    for (const o of orders) for (const p of pays) for (const i of invs) {
      const flow = deriveFlow(order(o), p ? [payment(p)] : [], i ? [invoice(i)] : []);
      const waiting = [flow.order, flow.payment, flow.invoice].includes('waiting');
      expect(flow.final, `${o}/${p}/${i}`).toBe(!waiting);
    }
  });
});
