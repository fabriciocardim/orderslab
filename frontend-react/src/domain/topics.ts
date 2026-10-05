export interface TopicInfo {
  name: string;
  producer: 'order-api' | 'payment-api' | 'invoice-api';
  kind: 'event' | 'dlt';
  note: string;
}

export const TOPICS: TopicInfo[] = [
  { name: 'order.created', producer: 'order-api', kind: 'event', note: 'pedido criado' },
  { name: 'order.confirmed', producer: 'order-api', kind: 'event', note: 'pedido confirmado' },
  { name: 'order.cancelled', producer: 'order-api', kind: 'event', note: 'pedido cancelado' },
  { name: 'payment.reserved', producer: 'payment-api', kind: 'event', note: 'pagamento reservado' },
  { name: 'payment.failed', producer: 'payment-api', kind: 'event', note: 'pagamento falhou' },
  { name: 'invoice.issued', producer: 'invoice-api', kind: 'event', note: 'nota emitida' },
  { name: 'invoice.failed', producer: 'invoice-api', kind: 'event', note: 'nota falhou' },
  { name: 'order.created.dlt', producer: 'payment-api', kind: 'dlt', note: 'mensagens que o payment-api não conseguiu processar' },
  { name: 'payment.reserved.dlt', producer: 'invoice-api', kind: 'dlt', note: 'mensagens que o invoice-api não conseguiu processar' },
];
