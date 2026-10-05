import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { getConfig, kafbatTopicUrl } from '../config';
import { TopicsCard } from './TopicsCard';

afterEach(() => { delete window.__LAB_CONFIG__; });

const EXPECTED = [
  'order.created', 'order.confirmed', 'order.cancelled', 'payment.reserved', 'payment.failed',
  'invoice.issued', 'invoice.failed', 'order.created.dlt', 'payment.reserved.dlt',
];

describe('TopicsCard', () => {
  it('lista os 7 tópicos e os 2 DLTs, cada um com link do Kafbat (padrão: localhost:8090, cluster orderslab)', () => {
    render(<TopicsCard />);
    for (const topic of EXPECTED) {
      const link = screen.getByRole('link', { name: topic });
      expect(link).toHaveAttribute('href', `http://localhost:8090/ui/clusters/orderslab/all-topics/${topic}`);
    }
    expect(screen.getAllByRole('link', { name: '(mensagens)' })).toHaveLength(9);
  });

  it('trocar a URL base e o cluster em runtime muda os links, sem recompilar', () => {
    window.__LAB_CONFIG__ = { kafbatUrl: 'https://kafbat.exemplo/', kafbatCluster: 'meu-cluster' };
    render(<TopicsCard />);
    expect(screen.getByRole('link', { name: 'order.created' })).toHaveAttribute(
      'href', 'https://kafbat.exemplo/ui/clusters/meu-cluster/all-topics/order.created');
  });
});

describe('config', () => {
  it('sem config.js usa os padrões', () => {
    expect(getConfig()).toEqual({ kafbatUrl: 'http://localhost:8090', kafbatCluster: 'orderslab' });
  });

  it('config parcial completa com os padrões e remove a barra final', () => {
    window.__LAB_CONFIG__ = { kafbatUrl: 'http://kafbat:8090///' };
    expect(getConfig()).toEqual({ kafbatUrl: 'http://kafbat:8090', kafbatCluster: 'orderslab' });
    expect(kafbatTopicUrl('payment.failed', 'messages')).toBe(
      'http://kafbat:8090/ui/clusters/orderslab/all-topics/payment.failed/messages');
  });
});
