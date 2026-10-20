// Mesma regra de frontend-react/src/domain/flow.ts, sobre o corpo cru das respostas de pagamentos e notas.
// Com o filtro por pedido (spec 021) a resposta já traz só os registros do pedido, mas a busca no texto continua
// servindo para uma lista qualquer (inclusive a lista completa de um serviço antigo) sem JSON.parse no corpo todo.

/** Registros (planos, sem objetos aninhados) do pedido dentro do JSON de uma lista; o mais recente. */
export function latestFor(body, orderId) {
  if (typeof body !== 'string') return undefined;
  const needle = `"orderId":"${orderId}"`;
  let latest;
  let at = body.indexOf(needle);
  while (at !== -1) {
    const start = body.lastIndexOf('{', at);
    const end = body.indexOf('}', at);
    try {
      const record = JSON.parse(body.slice(start, end + 1));
      if (!latest || record.createdAt > latest.createdAt) latest = record;
    } catch (_) {
      // corpo truncado/inesperado: ignora este trecho
    }
    at = body.indexOf(needle, end);
  }
  return latest;
}

export function paymentStep(order, payment) {
  if (payment) {
    switch (payment.status) {
      case 'RESERVED':
      case 'CONFIRMED':
        return 'done';
      case 'FAILED':
        return 'failed';
      case 'CANCELLED':
        return 'cancelled';
    }
  }
  return order && order.status === 'CANCELLED' ? 'not_applicable' : 'waiting';
}

export function invoiceStep(payment, invoice) {
  if (invoice) {
    switch (invoice.status) {
      case 'ISSUED':
        return 'done';
      case 'FAILED':
        return 'failed';
      case 'CANCELLED':
        return 'cancelled';
      case 'PENDING':
        return 'waiting';
    }
  }
  return payment === 'done' || payment === 'waiting' ? 'waiting' : 'not_applicable';
}

/** Desfecho esperado pelas regras atuais: pagamento falha acima de 1000; nota falha acima de 500. */
export function expectedOutcome(amount, paymentLimit, invoiceLimit) {
  if (amount > paymentLimit) return 'pagamento_falhou';
  if (amount > invoiceLimit) return 'nota_falhou';
  return 'nota_emitida';
}

export function outcomeOf(payment, invoice) {
  if (payment === 'failed') return 'pagamento_falhou';
  if (invoice === 'failed') return 'nota_falhou';
  if (invoice === 'done') return 'nota_emitida';
  return `outro(${payment}/${invoice})`;
}
