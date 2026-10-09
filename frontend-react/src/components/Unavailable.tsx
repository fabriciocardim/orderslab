interface Props {
  what: string;
  lastKnown?: boolean;
}

/** Aviso padrão de serviço indisponível — nunca deixa a área em branco sem explicação. */
export function Unavailable({ what, lastKnown = false }: Props) {
  return (
    <p className="unavailable" role="status">
      {what} indisponível{lastKnown ? ' — mostrando o último dado conhecido' : ''}.
    </p>
  );
}
