-- Daily interest accrual records
CREATE TABLE IF NOT EXISTS interest_accruals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)   NOT NULL,
    account_id      UUID          NOT NULL REFERENCES accounts(id),
    accrual_date    DATE          NOT NULL,
    balance_used    NUMERIC(18,2) NOT NULL,
    rate            NUMERIC(10,4) NOT NULL,
    daily_amount    NUMERIC(18,4) NOT NULL,
    posted          BOOLEAN       NOT NULL DEFAULT false,
    posting_id      UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    UNIQUE(account_id, accrual_date)
);

CREATE INDEX IF NOT EXISTS idx_interest_accruals_account ON interest_accruals(account_id);
CREATE INDEX IF NOT EXISTS idx_interest_accruals_date ON interest_accruals(accrual_date);
CREATE INDEX IF NOT EXISTS idx_interest_accruals_unposted ON interest_accruals(account_id, posted) WHERE posted = false;

-- Periodic interest postings (credits to account)
CREATE TABLE IF NOT EXISTS interest_postings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(50)   NOT NULL,
    account_id          UUID          NOT NULL REFERENCES accounts(id),
    period_start        DATE          NOT NULL,
    period_end          DATE          NOT NULL,
    gross_interest      NUMERIC(18,4) NOT NULL,
    withholding_tax     NUMERIC(18,4) NOT NULL DEFAULT 0,
    net_interest        NUMERIC(18,4) NOT NULL,
    transaction_id      UUID,
    posted_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    posted_by           VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_interest_postings_account ON interest_postings(account_id);
CREATE INDEX IF NOT EXISTS idx_interest_postings_period ON interest_postings(period_start, period_end);
