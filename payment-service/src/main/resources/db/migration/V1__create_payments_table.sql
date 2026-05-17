-- ============================================================
-- V1: Create payments table
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS payment_sequence
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS payments (
    id                  BIGINT          NOT NULL DEFAULT nextval('payment_sequence'),
    payment_id          VARCHAR(50)     NOT NULL,
    order_number        VARCHAR(50)     NOT NULL,
    customer_id         VARCHAR(255)    NOT NULL,
    customer_email      VARCHAR(255)    NOT NULL,
    amount              NUMERIC(12, 2)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    method              VARCHAR(30)     NOT NULL,
    payment_token       VARCHAR(100),
    gateway_reference   VARCHAR(100),
    failure_reason      TEXT,
    refund_reference    VARCHAR(100),
    refund_amount       NUMERIC(12, 2),
    refunded_at         TIMESTAMP,
    attempt_count       INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_payments              PRIMARY KEY (id),
    CONSTRAINT uq_payments_payment_id   UNIQUE (payment_id),
    CONSTRAINT uq_payments_order_number UNIQUE (order_number),
    CONSTRAINT chk_payments_amount      CHECK (amount > 0),
    CONSTRAINT chk_payments_status      CHECK (status IN (
        'PENDING','PROCESSING','COMPLETED','FAILED','REFUNDED','PARTIALLY_REFUNDED'
    )),
    CONSTRAINT chk_payments_method      CHECK (method IN ('CARD','BANK_TRANSFER','WALLET'))
);

-- Indexes
CREATE INDEX idx_payment_order_number ON payments (order_number);
CREATE INDEX idx_payment_customer     ON payments (customer_id);
CREATE INDEX idx_payment_status       ON payments (status);
CREATE INDEX idx_payment_reference    ON payments (gateway_reference);
CREATE INDEX idx_payment_created      ON payments (created_at DESC);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
