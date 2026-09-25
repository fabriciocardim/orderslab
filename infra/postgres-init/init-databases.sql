-- Provisionamento de bancos e usuários dedicados por serviço (item 1.9 do ROADMAP).
--
-- Roda automaticamente na primeira inicialização do container postgres-api (mecanismo
-- oficial da imagem postgres: scripts em /docker-entrypoint-initdb.d/ só rodam quando o
-- volume de dados está vazio). CREATE DATABASE/CREATE ROLE/GRANT são comandos de nível de
-- cluster, então funcionam independente de qual "banco atual" este script está conectado.
--
-- Cada serviço (order-api, payment-api, invoice-api) ganha seu próprio banco + usuário,
-- com privilégios completos só no próprio banco — "database per service" dentro de uma
-- única instância Postgres compartilhada (Princípio I da constitution).

-- IMPORTANTE: o Postgres concede CONNECT a PUBLIC em todo banco novo por padrão — sem
-- revogar isso explicitamente, qualquer usuário conseguiria conectar em qualquer banco,
-- furando o isolamento que este script existe para garantir.

-- order-api
CREATE DATABASE order_db;
CREATE USER order_user WITH PASSWORD 'order_password';
GRANT ALL PRIVILEGES ON DATABASE order_db TO order_user;
ALTER DATABASE order_db OWNER TO order_user;
REVOKE CONNECT ON DATABASE order_db FROM PUBLIC;

-- payment-api
CREATE DATABASE payment_db;
CREATE USER payment_user WITH PASSWORD 'payment_password';
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_user;
ALTER DATABASE payment_db OWNER TO payment_user;
REVOKE CONNECT ON DATABASE payment_db FROM PUBLIC;

-- invoice-api
CREATE DATABASE invoice_db;
CREATE USER invoice_user WITH PASSWORD 'invoice_password';
GRANT ALL PRIVILEGES ON DATABASE invoice_db TO invoice_user;
ALTER DATABASE invoice_db OWNER TO invoice_user;
REVOKE CONNECT ON DATABASE invoice_db FROM PUBLIC;
