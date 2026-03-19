-- Deposit products table
CREATE TABLE IF NOT EXISTS deposit_products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)  NOT NULL,
    product_code    VARCHAR(50)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    product_category VARCHAR(30) NOT NULL CHECK (product_category IN ('SAVINGS','CURRENT','FIXED_DEPOSIT','CALL_DEPOSIT','WALLET')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','PAUSED','ARCHIVED')),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'KES',

    -- Interest configuration
    interest_rate               NUMERIC(10,4) NOT NULL DEFAULT 0,
    interest_calc_method        VARCHAR(30)   NOT NULL DEFAULT 'DAILY_BALANCE' CHECK (interest_calc_method IN ('DAILY_BALANCE','MINIMUM_BALANCE','TIERED')),
    interest_posting_freq       VARCHAR(20)   NOT NULL DEFAULT 'MONTHLY' CHECK (interest_posting_freq IN ('MONTHLY','QUARTERLY','ANNUALLY','ON_MATURITY')),
    interest_compound_freq      VARCHAR(20)   NOT NULL DEFAULT 'MONTHLY' CHECK (interest_compound_freq IN ('DAILY','MONTHLY','NONE')),
    accrual_frequency           VARCHAR(20)   NOT NULL DEFAULT 'DAILY' CHECK (accrual_frequency IN ('DAILY','MONTHLY')),

    -- Balance rules
    min_opening_balance         NUMERIC(18,2) NOT NULL DEFAULT 0,
    min_operating_balance       NUMERIC(18,2) NOT NULL DEFAULT 0,
    min_balance_for_interest    NUMERIC(18,2) NOT NULL DEFAULT 0,

    -- Fixed deposit
    min_term_days               INT,
    max_term_days               INT,
    early_withdrawal_penalty_rate NUMERIC(10,4),
    auto_renew                  BOOLEAN NOT NULL DEFAULT false,

    -- Dormancy
    dormancy_days_threshold     INT NOT NULL DEFAULT 365,
    dormancy_charge_amount      NUMERIC(18,2),

    -- Fees
    monthly_maintenance_fee     NUMERIC(18,2),

    -- Withdrawal limits
    max_withdrawals_per_month   INT,

    -- Metadata
    version         INT          NOT NULL DEFAULT 1,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    UNIQUE(tenant_id, product_code)
);

CREATE INDEX IF NOT EXISTS idx_deposit_products_tenant ON deposit_products(tenant_id);
CREATE INDEX IF NOT EXISTS idx_deposit_products_category ON deposit_products(product_category);
CREATE INDEX IF NOT EXISTS idx_deposit_products_status ON deposit_products(status);

-- Tiered interest rates for deposit products
CREATE TABLE IF NOT EXISTS deposit_interest_tiers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES deposit_products(id) ON DELETE CASCADE,
    from_amount     NUMERIC(18,2) NOT NULL,
    to_amount       NUMERIC(18,2) NOT NULL,
    rate            NUMERIC(10,4) NOT NULL,

    CONSTRAINT chk_tier_range CHECK (to_amount > from_amount)
);

CREATE INDEX IF NOT EXISTS idx_deposit_interest_tiers_product ON deposit_interest_tiers(product_id);
