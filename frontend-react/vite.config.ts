import react from '@vitejs/plugin-react';

// mesmas variáveis de ambiente que infra/docker-compose.yml declara para o serviço "frontend"
const orderApiUrl = process.env.ORDER_API_URL ?? 'http://order-service:8080';
const paymentApiUrl = process.env.PAYMENT_API_URL ?? 'http://payment-service:8080';
const invoiceApiUrl = process.env.INVOICE_API_URL ?? 'http://invoice-service:8080';

const health = (target: string) => ({ target, rewrite: () => '/actuator/health' });

export default {
  plugins: [react()],
  server: {
    proxy: {
      '/api/orders': orderApiUrl,
      '/api/payments': paymentApiUrl,
      '/api/invoices': invoiceApiUrl,
      '/health/order': health(orderApiUrl),
      '/health/payment': health(paymentApiUrl),
      '/health/invoice': health(invoiceApiUrl),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    // poucos workers e timeout folgado: máquinas de 8 GB / CI pequeno ficam em swap com muitos jsdom em paralelo
    maxWorkers: 2,
    testTimeout: 15_000,
  },
};
