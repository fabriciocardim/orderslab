import type { ListPage, Payment } from '../domain/types';
import { LIST_LIMIT, request, requestList } from './http';

/** Os pagamentos mais recentes + o total existente. */
export const listPayments = (limit = LIST_LIMIT): Promise<ListPage<Payment>> =>
  requestList<Payment>(`/api/payments?limit=${limit}`);

/** Só os pagamentos de um pedido (lista vazia enquanto o pagamento não chegou). */
export const listPaymentsByOrder = (orderId: string) =>
  request<Payment[]>(`/api/payments?orderId=${encodeURIComponent(orderId)}`);
