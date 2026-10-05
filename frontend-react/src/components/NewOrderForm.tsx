import { useState } from 'react';
import type { FormEvent } from 'react';
import { createOrder } from '../api/orders';
import { ApiError } from '../domain/types';

interface Props {
  onCreated: (orderId: string) => void;
}

interface FieldErrors {
  customerId?: string;
  amount?: string;
  general?: string;
}

/** Mapeia as mensagens "campo: texto" do 400 do order-api para os campos do formulário. */
function fromServer(error: ApiError): FieldErrors {
  const errors: FieldErrors = {};
  for (const entry of error.validationErrors) {
    const [field, ...rest] = entry.split(':');
    const message = rest.join(':').trim() || entry;
    if (field.trim() === 'customerId') errors.customerId = message;
    else if (field.trim() === 'amount') errors.amount = message;
    else errors.general = errors.general ? `${errors.general}; ${entry}` : entry;
  }
  if (Object.keys(errors).length === 0) errors.general = error.message;
  return errors;
}

export function NewOrderForm({ onCreated }: Props) {
  const [customerId, setCustomerId] = useState('');
  const [amount, setAmount] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [sending, setSending] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const local: FieldErrors = {};
    const value = Number(amount.replace(',', '.'));
    if (!customerId.trim()) local.customerId = 'Informe o cliente.';
    if (amount.trim() === '' || Number.isNaN(value)) local.amount = 'Informe o valor.';
    else if (value <= 0) local.amount = 'O valor deve ser maior que zero.';
    if (local.customerId || local.amount) {
      setErrors(local);
      return;
    }
    setErrors({});
    setSending(true);
    try {
      const order = await createOrder({ customerId: customerId.trim(), amount: value });
      setCustomerId('');
      setAmount('');
      onCreated(order.id);
    } catch (error) {
      setErrors(error instanceof ApiError ? fromServer(error) : { general: 'Não foi possível criar o pedido.' });
    } finally {
      setSending(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label="Novo pedido">
      <div className="field">
        <label htmlFor="customerId">Cliente</label>
        <input id="customerId" value={customerId} onChange={(e) => setCustomerId(e.target.value)}
          aria-invalid={Boolean(errors.customerId)} aria-describedby={errors.customerId ? 'customerId-error' : undefined} />
        {errors.customerId && <span id="customerId-error" className="error">{errors.customerId}</span>}
      </div>
      <div className="field">
        <label htmlFor="amount">Valor</label>
        <input id="amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)}
          aria-invalid={Boolean(errors.amount)} aria-describedby={errors.amount ? 'amount-error' : undefined} />
        {errors.amount && <span id="amount-error" className="error">{errors.amount}</span>}
      </div>
      {errors.general && <p className="error" role="alert">{errors.general}</p>}
      <button type="submit" disabled={sending}>{sending ? 'Criando…' : 'Criar pedido'}</button>
    </form>
  );
}
