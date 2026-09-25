CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    payment_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
