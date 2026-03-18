CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE scoring_requests (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(50) NOT NULL,
    loan_application_id UUID NOT NULL,
    customer_id         BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    trigger_event       VARCHAR(100),
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE scoring_results (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(50) NOT NULL,
    request_id          UUID NOT NULL REFERENCES scoring_requests(id),
    loan_application_id UUID NOT NULL,
    customer_id         BIGINT NOT NULL,
    base_score          NUMERIC(8,2),
    crb_contribution    NUMERIC(8,2),
    llm_adjustment      NUMERIC(8,2),
    pd_probability      NUMERIC(8,6),
    final_score         NUMERIC(8,2),
    score_band          VARCHAR(50),
    reasoning           TEXT,
    llm_provider        VARCHAR(50),
    llm_model           VARCHAR(100),
    raw_response        TEXT,
    scored_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scoring_requests_loan ON scoring_requests(loan_application_id);
CREATE INDEX idx_scoring_requests_customer ON scoring_requests(customer_id);
CREATE INDEX idx_scoring_requests_status ON scoring_requests(status);
CREATE INDEX idx_scoring_results_loan ON scoring_results(loan_application_id);
CREATE INDEX idx_scoring_results_customer ON scoring_results(customer_id);
CREATE INDEX idx_scoring_results_request ON scoring_results(request_id);
