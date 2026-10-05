import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const health = (target: string) => ({ target, rewrite: () => '/actuator/health' });

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/orders': 'http://localhost:8081',
      '/api/payments': 'http://localhost:8082',
      '/api/invoices': 'http://localhost:8083',
      '/health/order': health('http://localhost:8081'),
      '/health/payment': health('http://localhost:8082'),
      '/health/invoice': health('http://localhost:8083'),
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
});
