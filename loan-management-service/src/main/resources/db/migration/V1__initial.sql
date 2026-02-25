-- Loan Management Service: V1__initial.sql
-- Tables: loans, loan_schedules, loan_repayments, loan_dpd_history

CREATE TABLE IF NOT EXISTS loans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(50)     NOT NULL,
    application_id          UUID            NOT NULL,
    customer_id             UUID            NOT NULL,
    product_id              UUID            NOT NULL,
    disbursed_amount        NUMERIC(18,2)   NOT NULL,
    outstanding_principal   NUMERIC(18,2)   NOT NULL,
    outstanding_interest    NUMERIC(18,2)   NOT NULL DEFAULT 0,
    outstanding_fees        NUMERIC(18,2)   NOT NULL DEFAULT 0,
    outstanding_penalty     NUMERIC(18,2)   NOT NULL DEFAULT 0,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'KES',
    interest_rate           NUMERIC(8,4)    NOT NULL,
    tenor_months            INTEGER         NOT NULL,
    repayment_frequency     VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',
    schedule_type           VARCHAR(20)     NOT NULL DEFAULT 'EMI',
    disbursed_at            TIMESTAMPTZ     NOT NULL,
    first_repayment_date    DATE            NOT NULL,
    maturity_date           DATE            NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    stage                   VARCHAR(30)     NOT NULL DEFAULT 'PERFORMING',
    dpd                     INTEGER         NOT NULL DEFAULT 0,
    last_repayment_date     DATE,
    last_repayment_amount   NUMERIC(18,2),
    closed_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS loan_schedules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID            NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    tenant_id           VARCHAR(50)     NOT NULL,
    installment_no      INTEGER         NOT NULL,
    due_date            DATE            NOT NULL,
    principal_due       NUMERIC(18,2)   NOT NULL DEFAULT 0,
    interest_due        NUMERIC(18,2)   NOT NULL DEFAULT 0,
    fee_due             NUMERIC(18,2)   NOT NULL DEFAULT 0,
    penalty_due         NUMERIC(18,2)   NOT NULL DEFAULT 0,
    total_due           NUMERIC(18,2)   NOT NULL DEFAULT 0,
    principal_paid      NUMERIC(18,2)   NOT NULL DEFAULT 0,
    interest_paid       NUMERIC(18,2)   NOT NULL DEFAULT 0,
    fee_paid            NUMERIC(18,2)   NOT NULL DEFAULT 0,
    penalty_paid        NUMERIC(18,2)   NOT NULL DEFAULT 0,
    total_paid          NUMERIC(18,2)   NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    paid_date           DATE,
    UNIQUE(loan_id, installment_no)
);

CREATE TABLE IF NOT EXISTS loan_repayments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID            NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    tenant_id           VARCHAR(50)     NOT NULL,
    amount              NUMERIC(18,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'KES',
    penalty_applied     NUMERIC(18,2)   NOT NULL DEFAULT 0,
    fee_applied         NUMERIC(18,2)   NOT NULL DEFAULT 0,
    interest_applied    NUMERIC(18,2)   NOT NULL DEFAULT 0,
    principal_applied   NUMERIC(18,2)   NOT NULL DEFAULT 0,
    payment_reference   VARCHAR(100),
    payment_method      VARCHAR(50),
    payment_date        DATE            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS loan_dpd_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id     UUID            NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    tenant_id   VARCHAR(50)     NOT NULL,
    dpd         INTEGER         NOT NULL,
    stage       VARCHAR(30)     NOT NULL,
    snapshot_date DATE          NOT NULL DEFAULT CURRENT_DATE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_loans_tenant          ON loans(tenant_id);
CREATE INDEX idx_loans_customer        ON loans(customer_id);
CREATE INDEX idx_loans_status          ON loans(status);
CREATE INDEX idx_loans_stage           ON loans(stage);
CREATE INDEX idx_loans_tenant_status   ON loans(tenant_id, status);
CREATE INDEX idx_schedules_loan        ON loan_schedules(loan_id);
CREATE INDEX idx_schedules_due_date    ON loan_schedules(due_date);
CREATE INDEX idx_repayments_loan       ON loan_repayments(loan_id);
CREATE INDEX idx_dpd_history_loan      ON loan_dpd_history(loan_id);
