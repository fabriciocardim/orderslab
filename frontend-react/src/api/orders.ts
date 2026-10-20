import type { ListPage, Order } from '../domain/types';
import { LIST_LIMIT, request, requestList } from './http';

export interface NewOrder {
  customerId: string;
  amount: number;
}

const json = { 'Content-Type': 'application/json' };

export const createOrder = (order: NewOrder) =>
  request<Order>('/api/orders', { method: 'POST', headers: json, body: JSON.stringify(order) });

/** Os pedidos mais recentes + o total existente. */
export const listOrders = (limit = LIST_LIMIT): Promise<ListPage<Order>> => requestList<Order>(`/api/orders?limit=${limit}`);

export const getOrder = (id: string) => request<Order>(`/api/orders/${encodeURIComponent(id)}`);

export const confirmOrder = (id: string) =>
  request<Order>(`/api/orders/${encodeURIComponent(id)}/confirm`, { method: 'POST' });

export const cancelOrder = (id: string) =>
  request<Order>(`/api/orders/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
