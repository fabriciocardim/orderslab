import { useState } from 'react';
import { listInvoices } from './api/invoices';
import { listOrders } from './api/orders';
import { listPayments } from './api/payments';
import { FlowTracker } from './components/FlowTracker';
import { HealthPanel } from './components/HealthPanel';
import { NewOrderForm } from './components/NewOrderForm';
import { ScenarioButtons } from './components/ScenarioButtons';
import { ServiceTable } from './components/ServiceTable';
import { TopicsCard } from './components/TopicsCard';
import type { Invoice, Order, Payment } from './domain/types';

type Tab = 'flow' | 'lists' | 'kafka' | 'health';

const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'flow', label: 'Novo pedido e fluxo' },
  { id: 'lists', label: 'Listagens' },
  { id: 'kafka', label: 'Kafka' },
  { id: 'health', label: 'Saúde' },
];

export function App() {
  const [tab, setTab] = useState<Tab>('flow');
  const [trackedOrder, setTrackedOrder] = useState<string | null>(null);

  const track = (orderId: string) => {
    setTrackedOrder(orderId);
    setTab('flow');
  };

  return (
    <main>
      <h1>orderslab — UI do laboratório</h1>
      <nav aria-label="Seções">
        {TABS.map((t) => (
          <button key={t.id} type="button" aria-pressed={tab === t.id} onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </nav>

      {tab === 'flow' && (
        <div className="grid">
          <section aria-label="Criar pedido">
            <h2>Novo pedido</h2>
            <NewOrderForm onCreated={track} />
            <h3>Cenários prontos</h3>
            <ScenarioButtons onCreated={track} />
          </section>
          <section aria-label="Fluxo">
            <h2>Acompanhamento</h2>
            <FlowTracker orderId={trackedOrder} />
          </section>
        </div>
      )}

      {tab === 'lists' && (
        <div className="lists">
          <ServiceTable<Order>
            title="Pedidos" service="order-api" name="orders" fetcher={listOrders} rowKey={(o) => o.id}
            columns={[
              { header: 'Id', cell: (o) => o.id.slice(0, 8) },
              { header: 'Cliente', cell: (o) => o.customerId },
              { header: 'Valor', cell: (o) => o.amount.toFixed(2) },
              { header: 'Status', cell: (o) => o.status },
              { header: '', cell: (o) => <button type="button" onClick={() => track(o.id)}>Acompanhar</button> },
            ]}
          />
          <ServiceTable<Payment>
            title="Pagamentos" service="payment-api" name="payments" fetcher={listPayments} rowKey={(p) => p.id}
            columns={[
              { header: 'Id', cell: (p) => p.id.slice(0, 8) },
              { header: 'Pedido', cell: (p) => p.orderId.slice(0, 8) },
              { header: 'Valor', cell: (p) => p.amount.toFixed(2) },
              { header: 'Status', cell: (p) => p.status },
            ]}
          />
          <ServiceTable<Invoice>
            title="Notas" service="invoice-api" name="invoices" fetcher={listInvoices} rowKey={(i) => i.id}
            columns={[
              { header: 'Id', cell: (i) => i.id.slice(0, 8) },
              { header: 'Pedido', cell: (i) => i.orderId.slice(0, 8) },
              { header: 'Pagamento', cell: (i) => i.paymentId.slice(0, 8) },
              { header: 'Valor', cell: (i) => i.amount.toFixed(2) },
              { header: 'Status', cell: (i) => i.status },
            ]}
          />
        </div>
      )}

      {tab === 'kafka' && <TopicsCard />}
      {tab === 'health' && <HealthPanel />}
    </main>
  );
}
