import { ApiError } from '../domain/types';
import type { ListPage } from '../domain/types';

export const REQUEST_TIMEOUT_MS = 3000;

/** Quantos registros as listagens pedem aos serviços (os mais recentes); o serviço aceita de 1 a 500. */
export const LIST_LIMIT = 200;

interface ErrorBody {
  message?: string;
  status?: number;
  path?: string;
  validationErrors?: string[];
}

/** fetch com timeout; resposta não-2xx vira ApiError (lendo o ErrorResponse do serviço, se houver). */
async function send(path: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(path, { ...init, signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
  } catch {
    throw new ApiError(0, 'Serviço indisponível (sem resposta em 3 s)');
  }

  if (!response.ok) {
    let body: ErrorBody = {};
    try {
      body = (await response.json()) as ErrorBody;
    } catch {
      // corpo vazio ou não-JSON (ex.: 502 do proxy)
    }
    throw new ApiError(
      response.status,
      body.message ?? `HTTP ${response.status}`,
      Array.isArray(body.validationErrors) ? body.validationErrors : [],
      body.path,
    );
  }
  return response;
}

async function json<T>(response: Response): Promise<T> {
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(0, 'Resposta inesperada do serviço');
  }
}

/**
 * fetch com timeout de 3 s. Resposta não-2xx vira ApiError (lendo o ErrorResponse do serviço, se houver);
 * rede/timeout/corpo inválido viram ApiError de status 0 ("indisponível"). Sem retry: o polling é o retry.
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return json<T>(await send(path, init));
}

/**
 * Lista limitada: o corpo traz os mais recentes e o cabeçalho X-Total-Count o total existente no serviço
 * (sem o cabeçalho, o total é o próprio tamanho da lista). Mesmo tratamento de erro de `request`.
 */
export async function requestList<T>(path: string): Promise<ListPage<T>> {
  const response = await send(path);
  const items = await json<T[]>(response);
  if (!Array.isArray(items)) throw new ApiError(0, 'Resposta inesperada do serviço');
  const header = Number(response.headers.get('X-Total-Count'));
  return { items, total: Number.isFinite(header) && header >= items.length ? header : items.length };
}
