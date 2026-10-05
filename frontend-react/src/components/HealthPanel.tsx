import { useQuery } from '@tanstack/react-query';
import { getHealth } from '../api/health';
import type { HealthService } from '../api/health';
import { POLL_INTERVAL_MS } from '../hooks/useTrackedOrder';

const SERVICES: Array<{ id: HealthService; name: string }> = [
  { id: 'order', name: 'order-api' },
  { id: 'payment', name: 'payment-api' },
  { id: 'invoice', name: 'invoice-api' },
];

function ServiceHealth({ id, name }: { id: HealthService; name: string }) {
  const { data, isPending } = useQuery({
    queryKey: ['health', id],
    queryFn: () => getHealth(id),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: false,
  });
  const state = isPending ? 'checking' : data === 'UP' ? 'up' : 'down';
  const label = state === 'checking' ? 'verificando' : state === 'up' ? 'no ar' : 'fora do ar';
  return (
    <li data-testid={`health-${id}`} data-state={state}>
      <strong>{name}</strong> <span className="badge">{label}</span>
    </li>
  );
}

export function HealthPanel() {
  return (
    <section aria-label="Saúde dos serviços">
      <h3>Saúde dos serviços</h3>
      <ul className="health">
        {SERVICES.map((service) => <ServiceHealth key={service.id} {...service} />)}
      </ul>
    </section>
  );
}
