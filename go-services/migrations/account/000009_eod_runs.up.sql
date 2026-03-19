-- Track EOD batch runs for auditing and idempotency
CREATE TABLE IF NOT EXISTS eod_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)   NOT NULL,
    run_date        DATE          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','COMPLETED','PARTIAL','FAILED')),
    initiated_by    VARCHAR(100)  NOT NULL,
    started_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    -- Step results
    accounts_accrued     INT NOT NULL DEFAULT 0,
    accrual_errors       INT NOT NULL DEFAULT 0,
    dormant_detected     INT NOT NULL DEFAULT 0,
    dormancy_errors      INT NOT NULL DEFAULT 0,
    matured_processed    INT NOT NULL DEFAULT 0,
    maturity_errors      INT NOT NULL DEFAULT 0,
    interest_posted      INT NOT NULL DEFAULT 0,
    posting_errors       INT NOT NULL DEFAULT 0,
    fees_applied         INT NOT NULL DEFAULT 0,
    -- Error details
    error_details        JSONB,
    -- Totals for reconciliation
    total_interest_accrued  NUMERIC(18,4) DEFAULT 0,
    total_interest_posted   NUMERIC(18,4) DEFAULT 0,
    total_wht_deducted      NUMERIC(18,4) DEFAULT 0,

    UNIQUE(tenant_id, run_date)
);

CREATE INDEX IF NOT EXISTS idx_eod_runs_tenant_date ON eod_runs(tenant_id, run_date DESC);
