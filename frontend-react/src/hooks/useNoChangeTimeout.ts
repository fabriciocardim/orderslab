import { useCallback, useEffect, useState } from 'react';

export const NO_CHANGE_LIMIT_MS = 30_000;

/**
 * Para o acompanhamento depois de `limitMs` sem mudança do `fingerprint` (ou imediatamente quando o fluxo é final).
 * `resume()` recomeça a contagem (botão "Atualizar agora"). Sem setState síncrono em efeito: o "parou" é derivado
 * de uma chave (fingerprint + época) gravada só pelo callback do timer.
 */
export function useNoChangeTimeout(fingerprint: string, final: boolean, limitMs = NO_CHANGE_LIMIT_MS) {
  const [epoch, setEpoch] = useState(0);
  const [stoppedKey, setStoppedKey] = useState<string | null>(null);
  const key = `${fingerprint}#${epoch}`;

  useEffect(() => {
    if (final) return undefined;
    const timer = setTimeout(() => setStoppedKey(key), limitMs);
    return () => clearTimeout(timer);
  }, [key, final, limitMs]);

  const resume = useCallback(() => setEpoch((value) => value + 1), []);
  return { stopped: !final && stoppedKey === key, resume };
}
