-- Índices das consultas da UI (spec 021): filtro por pedido no polling do acompanhamento e listagem dos mais
-- recentes. O ux_payments_order_id_from_event existente é parcial (WHERE source_event_id IS NOT NULL) e não serve
-- para um WHERE order_id = ? simples.
CREATE INDEX ix_payments_order_id ON payments (order_id);

CREATE INDEX ix_payments_created_at ON payments (created_at DESC);
