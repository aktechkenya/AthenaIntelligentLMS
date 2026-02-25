-- Payment Service: V1__initial.sql

CREATE TABLE IF NOT EXISTS payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(50)     NOT NULL,
    customer_id         UUID            NOT NULL,
    loan_id             UUID,
    application_id      UUID,
    payment_type        VARCHAR(50)     NOT NULL,
    payment_channel     VARCHAR(50)     NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    amount              NUMERIC(18,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'KES',
    external_reference  VARCHAR(200),
    internal_reference  VARCHAR(100)    UNIQUE NOT NULL DEFAULT gen_random_uuid()::text,
    description         VARCHAR(500),
    failure_reason      TEXT,
    reversal_reason     TEXT,
    payment_method_id   UUID,
    initiated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    reversed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS payment_methods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)     NOT NULL,
    customer_id     UUID            NOT NULL,
    method_type     VARCHAR(30)     NOT NULL,
    alias           VARCHAR(100),
    account_number  VARCHAR(100)    NOT NULL,
    account_name    VARCHAR(200),
    provider        VARCHAR(100),
    is_default      BOOLEAN         NOT NULL DEFAULT false,
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_tenant          ON payments(tenant_id);
CREATE INDEX idx_payments_customer        ON payments(customer_id);
CREATE INDEX idx_payments_status          ON payments(status);
CREATE INDEX idx_payments_loan_id         ON payments(loan_id);
CREATE INDEX idx_payments_ext_ref         ON payments(external_reference);
CREATE INDEX idx_payments_tenant_status   ON payments(tenant_id, status);
CREATE INDEX idx_payment_methods_customer ON payment_methods(customer_id);
CREATE INDEX idx_payment_methods_tenant   ON payment_methods(tenant_id);
