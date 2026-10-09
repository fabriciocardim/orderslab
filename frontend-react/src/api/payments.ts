import type { Payment } from '../domain/types';
import { request } from './http';

export const listPayments = () => request<Payment[]>('/api/payments');
