import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { renderWithClient } from '../test/utils';
import { HealthPanel } from './HealthPanel';

const up = () => HttpResponse.json({ status: 'UP' });

describe('HealthPanel', () => {
  it('mostra "verificando" e depois "no ar" para os três serviços', async () => {
    server.use(http.get('*/health/order', up), http.get('*/health/payment', up), http.get('*/health/invoice', up));
    renderWithClient(<HealthPanel />);

    expect(screen.getByTestId('health-order')).toHaveAttribute('data-state', 'checking');
    for (const svc of ['order', 'payment', 'invoice']) {
      expect(await screen.findByText('no ar', { selector: `[data-testid="health-${svc}"] .badge` })).toBeInTheDocument();
    }
  });

  it('um serviço DOWN marca só ele como "fora do ar"', async () => {
    server.use(
      http.get('*/health/order', up),
      http.get('*/health/payment', () => HttpResponse.json({ status: 'DOWN' }, { status: 503 })),
      http.get('*/health/invoice', up),
    );
    renderWithClient(<HealthPanel />);

    expect(await screen.findByText('fora do ar')).toBeInTheDocument();
    expect(screen.getByTestId('health-payment')).toHaveAttribute('data-state', 'down');
    expect(await screen.findAllByText('no ar')).toHaveLength(2);
  });
});
