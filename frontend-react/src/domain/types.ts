export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';
export type PaymentStatus = 'RESERVED' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';
export type InvoiceStatus = 'PENDING' | 'ISSUED' | 'CANCELLED' | 'FAILED';

export interface Order {
  id: string;
  customerId: string;
  amount: number;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  amount: number;
  status: PaymentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Invoice {
  id: string;
  orderId: string;
  paymentId: string;
  amount: number;
  status: InvoiceStatus;
  createdAt: string;
  updatedAt: string;
}

export type Health = 'UP' | 'DOWN' | 'UNKNOWN';

export type StepState = 'done' | 'failed' | 'waiting' | 'not_applicable' | 'cancelled';

export interface Flow {
  order: StepState;
  payment: StepState;
  invoice: StepState;
  /** Fluxo final = nenhuma etapa aguardando. */
  final: boolean;
}

/** Erro devolvido pelas APIs (formato ErrorResponse) ou falha de rede/timeout (status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly validationErrors: string[];
  readonly path?: string;

  constructor(status: number, message: string, validationErrors: string[] = [], path?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.validationErrors = validationErrors;
    this.path = path;
  }

  /** Serviço indisponível: rede, timeout, 5xx ou 502/504 do proxy. */
  get unavailable(): boolean {
    return this.status === 0 || this.status >= 500;
  }
}
