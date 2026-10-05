import { kafbatTopicUrl } from '../config';
import { TOPICS } from '../domain/topics';

/** Cartão de tópicos e DLTs, cada um com deep link para o Kafbat (mensagens, chave, headers e filtros ficam lá). */
export function TopicsCard() {
  const groups = [
    { title: 'Tópicos de evento', items: TOPICS.filter((t) => t.kind === 'event') },
    { title: 'Dead-letter topics (DLT)', items: TOPICS.filter((t) => t.kind === 'dlt') },
  ];
  return (
    <section aria-label="Tópicos Kafka">
      <h3>Tópicos Kafka</h3>
      <p className="hint">Os links abrem o Kafbat UI, que mostra mensagens, chaves, headers e filtros.</p>
      {groups.map((group) => (
        <div key={group.title}>
          <h4>{group.title}</h4>
          <ul className="topics">
            {group.items.map((topic) => (
              <li key={topic.name}>
                <a href={kafbatTopicUrl(topic.name)} target="_blank" rel="noreferrer">{topic.name}</a>{' '}
                <a href={kafbatTopicUrl(topic.name, 'messages')} target="_blank" rel="noreferrer">(mensagens)</a>
                <small> — {topic.note} · produtor: {topic.producer}</small>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}
