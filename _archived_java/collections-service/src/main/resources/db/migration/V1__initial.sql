CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE collection_cases (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    loan_id         UUID NOT NULL,
    customer_id     BIGINT NOT NULL,
    case_number     VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    current_dpd     INT NOT NULL DEFAULT 0,
    current_stage   VARCHAR(30) NOT NULL DEFAULT 'WATCH',
    outstanding_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    assigned_to     VARCHAR(100),
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    last_action_at  TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_collection_case_loan UNIQUE (loan_id),
    CONSTRAINT uq_collection_case_number UNIQUE (tenant_id, case_number)
);

CREATE TABLE collection_actions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    case_id         UUID NOT NULL REFERENCES collection_cases(id),
    action_type     VARCHAR(50) NOT NULL,
    outcome         VARCHAR(50),
    notes           TEXT,
    contact_person  VARCHAR(200),
    contact_method  VARCHAR(50),
    performed_by    VARCHAR(100),
    performed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    next_action_date DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE promises_to_pay (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    case_id         UUID NOT NULL REFERENCES collection_cases(id),
    promised_amount NUMERIC(19,4) NOT NULL,
    promise_date    DATE NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    created_by      VARCHAR(100),
    fulfilled_at    TIMESTAMPTZ,
    broken_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_collection_cases_tenant ON collection_cases(tenant_id);
CREATE INDEX idx_collection_cases_loan ON collection_cases(loan_id);
CREATE INDEX idx_collection_cases_status ON collection_cases(status);
CREATE INDEX idx_collection_cases_dpd ON collection_cases(current_dpd);
CREATE INDEX idx_collection_actions_case ON collection_actions(case_id);
CREATE INDEX idx_promises_case ON promises_to_pay(case_id);
CREATE INDEX idx_promises_date ON promises_to_pay(promise_date);
