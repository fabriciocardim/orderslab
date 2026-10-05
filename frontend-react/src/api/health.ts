import type { Health } from '../domain/types';
import { REQUEST_TIMEOUT_MS } from './http';

export type HealthService = 'order' | 'payment' | 'invoice';

/**
 * Saúde via /health/{svc} (proxy → /actuator/health). Só é UP com 200 e {"status":"UP"}; 503 {"status":"DOWN"},
 * 502/504 do proxy, timeout, rede ou corpo inesperado contam como DOWN. Nunca lança.
 */
export async function getHealth(service: HealthService): Promise<Health> {
  try {
    const response = await fetch(`/health/${service}`, { signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
    if (!response.ok) return 'DOWN';
    const body = (await response.json()) as { status?: string };
    return body.status === 'UP' ? 'UP' : 'DOWN';
  } catch {
    return 'DOWN';
  }
}
