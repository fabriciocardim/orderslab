export interface LabConfig {
  kafbatUrl: string;
  kafbatCluster: string;
}

declare global {
  interface Window {
    __LAB_CONFIG__?: Partial<LabConfig>;
  }
}

const DEFAULTS: LabConfig = { kafbatUrl: 'http://localhost:8090', kafbatCluster: 'orderslab' };

/** Lê a configuração de runtime (config.js gerado no start do contêiner), com padrões se ausente. */
export function getConfig(): LabConfig {
  const runtime = typeof window === 'undefined' ? undefined : window.__LAB_CONFIG__;
  return {
    kafbatUrl: (runtime?.kafbatUrl || DEFAULTS.kafbatUrl).replace(/\/+$/, ''),
    kafbatCluster: runtime?.kafbatCluster || DEFAULTS.kafbatCluster,
  };
}

/** Deep link do Kafbat: /ui/clusters/{cluster}/all-topics/{tópico}[/messages]. */
export function kafbatTopicUrl(topic: string, tab?: 'messages'): string {
  const { kafbatUrl, kafbatCluster } = getConfig();
  const base = `${kafbatUrl}/ui/clusters/${encodeURIComponent(kafbatCluster)}/all-topics/${encodeURIComponent(topic)}`;
  return tab ? `${base}/${tab}` : base;
}
