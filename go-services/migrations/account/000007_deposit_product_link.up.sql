-- Expand account types to include FIXED_DEPOSIT and CALL_DEPOSIT
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_account_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_account_type_check
    CHECK (account_type IN ('CURRENT','SAVINGS','WALLET','FIXED_DEPOSIT','CALL_DEPOSIT'));

-- Expand account statuses to include PENDING_APPROVAL and MATURED
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_status_check;

-- Add deposit-product link and lifecycle columns
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS deposit_product_id  UUID,
    ADD COLUMN IF NOT EXISTS branch_id           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS opened_by           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS closed_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closure_reason       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_transaction_date TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dormant_since        TIMESTAMPTZ;

-- Fixed deposit columns
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS maturity_date        DATE,
    ADD COLUMN IF NOT EXISTS term_days            INT,
    ADD COLUMN IF NOT EXISTS locked_amount        NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS auto_renew           BOOLEAN DEFAULT false;

-- Interest columns
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS accrued_interest              NUMERIC(18,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_interest_accrual_date    DATE,
    ADD COLUMN IF NOT EXISTS last_interest_posting_date    DATE,
    ADD COLUMN IF NOT EXISTS interest_rate_override        NUMERIC(10,4);

CREATE INDEX IF NOT EXISTS idx_accounts_deposit_product ON accounts(deposit_product_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_accounts_type ON accounts(account_type);
CREATE INDEX IF NOT EXISTS idx_accounts_dormant ON accounts(dormant_since) WHERE dormant_since IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_accounts_maturity ON accounts(maturity_date) WHERE maturity_date IS NOT NULL;
