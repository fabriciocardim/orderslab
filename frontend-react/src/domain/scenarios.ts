export interface Scenario {
  id: string;
  label: string;
  amount: number;
  expected: string;
}

/** Limites atuais dos serviços: pagamento falha acima de 1000.00; nota falha acima de 500.00. */
export const SCENARIOS: Scenario[] = [
  { id: 'low', label: 'Valor baixo', amount: 10.5, expected: 'pagamento ok, nota emitida' },
  { id: 'limit-500', label: 'Exatamente 500', amount: 500, expected: 'pagamento ok, nota emitida (500 está no limite)' },
  { id: 'between', label: 'Entre 500 e 1000', amount: 750, expected: 'pagamento ok, nota falha' },
  { id: 'limit-1000', label: 'Exatamente 1000', amount: 1000, expected: 'pagamento ok (no limite), nota falha' },
  { id: 'high', label: 'Acima de 1000', amount: 1500, expected: 'pagamento falha, sem nota' },
];
