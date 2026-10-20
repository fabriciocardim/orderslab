import type { Invoice, ListPage } from '../domain/types';
import { LIST_LIMIT, request, requestList } from './http';

/** As notas mais recentes + o total existente. */
export const listInvoices = (limit = LIST_LIMIT): Promise<ListPage<Invoice>> =>
  requestList<Invoice>(`/api/invoices?limit=${limit}`);

/** Só as notas de um pedido (lista vazia enquanto a nota não chegou). */
export const listInvoicesByOrder = (orderId: string) =>
  request<Invoice[]>(`/api/invoices?orderId=${encodeURIComponent(orderId)}`);
