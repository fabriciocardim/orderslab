import type { Order } from '../domain/types';
import { request } from './http';

export interface NewOrder {
  customerId: string;
  amount: number;
}

const json = { 'Content-Type': 'application/json' };

export const createOrder = (order: NewOrder) =>
  request<Order>('/api/orders', { method: 'POST', headers: json, body: JSON.stringify(order) });

export const listOrders = () => request<Order[]>('/api/orders');

export const getOrder = (id: string) => request<Order>(`/api/orders/${encodeURIComponent(id)}`);

export const confirmOrder = (id: string) =>
  request<Order>(`/api/orders/${encodeURIComponent(id)}/confirm`, { method: 'POST' });

export const cancelOrder = (id: string) =>
  request<Order>(`/api/orders/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
