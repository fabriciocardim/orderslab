-- Índice da listagem dos mais recentes (spec 021): ORDER BY created_at DESC LIMIT n sem ordenar a tabela inteira.
CREATE INDEX ix_orders_created_at ON orders (created_at DESC);
