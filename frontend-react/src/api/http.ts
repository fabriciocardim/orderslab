import { ApiError } from '../domain/types';

export const REQUEST_TIMEOUT_MS = 3000;

interface ErrorBody {
  message?: string;
  status?: number;
  path?: string;
  validationErrors?: string[];
}

/**
 * fetch com timeout de 3 s. Resposta não-2xx vira ApiError (lendo o ErrorResponse do serviço, se houver);
 * rede/timeout/corpo inválido viram ApiError de status 0 ("indisponível"). Sem retry: o polling é o retry.
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
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

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(0, 'Resposta inesperada do serviço');
  }
}
