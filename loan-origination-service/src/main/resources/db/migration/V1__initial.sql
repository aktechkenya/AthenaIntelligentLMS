-- Loan Origination Service: V1__initial.sql
-- Tables: loan_applications, application_collaterals, application_notes, application_status_history

CREATE TABLE IF NOT EXISTS loan_applications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(50)     NOT NULL,
    customer_id         UUID            NOT NULL,
    product_id          UUID            NOT NULL,
    requested_amount    NUMERIC(18,2)   NOT NULL,
    approved_amount     NUMERIC(18,2),
    currency            VARCHAR(3)      NOT NULL DEFAULT 'KES',
    tenor_months        INTEGER         NOT NULL,
    purpose             VARCHAR(500),
    status              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    risk_grade          VARCHAR(5),
    credit_score        INTEGER,
    interest_rate       NUMERIC(8,4),
    disbursed_amount    NUMERIC(18,2),
    disbursed_at        TIMESTAMPTZ,
    disbursement_account VARCHAR(100),
    reviewer_id         UUID,
    reviewed_at         TIMESTAMPTZ,
    review_notes        TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS application_collaterals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID            NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(50)     NOT NULL,
    collateral_type VARCHAR(50)     NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    estimated_value NUMERIC(18,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'KES',
    document_ref    VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS application_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID            NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(50)     NOT NULL,
    note_type       VARCHAR(30)     NOT NULL DEFAULT 'UNDERWRITER',
    content         TEXT            NOT NULL,
    author_id       VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS application_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID            NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(50)     NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30)     NOT NULL,
    reason          TEXT,
    changed_by      VARCHAR(100),
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_loan_apps_tenant        ON loan_applications(tenant_id);
CREATE INDEX idx_loan_apps_customer      ON loan_applications(customer_id);
CREATE INDEX idx_loan_apps_status        ON loan_applications(status);
CREATE INDEX idx_loan_apps_tenant_status ON loan_applications(tenant_id, status);
CREATE INDEX idx_collaterals_app         ON application_collaterals(application_id);
CREATE INDEX idx_notes_app               ON application_notes(application_id);
CREATE INDEX idx_status_hist_app         ON application_status_history(application_id);
