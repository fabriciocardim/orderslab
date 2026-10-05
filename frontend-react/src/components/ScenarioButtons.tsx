import { useState } from 'react';
import { createOrder } from '../api/orders';
import { SCENARIOS } from '../domain/scenarios';
import type { Scenario } from '../domain/scenarios';

interface Props {
  onCreated: (orderId: string) => void;
}

export function ScenarioButtons({ onCreated }: Props) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(scenario: Scenario) {
    setBusy(scenario.id);
    setError(null);
    try {
      const order = await createOrder({ customerId: 'cliente-lab', amount: scenario.amount });
      onCreated(order.id);
    } catch (e) {
      setError(`Não foi possível criar o pedido do cenário "${scenario.label}": ${e instanceof Error ? e.message : 'erro'}`);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="scenarios">
      <ul>
        {SCENARIOS.map((scenario) => (
          <li key={scenario.id}>
            <button type="button" disabled={busy !== null} onClick={() => void run(scenario)}>
              {scenario.label} ({scenario.amount.toFixed(2)})
            </button>
            <small>{scenario.expected}</small>
          </li>
        ))}
      </ul>
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
