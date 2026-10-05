import type { Invoice } from '../domain/types';
import { request } from './http';

export const listInvoices = () => request<Invoice[]>('/api/invoices');
