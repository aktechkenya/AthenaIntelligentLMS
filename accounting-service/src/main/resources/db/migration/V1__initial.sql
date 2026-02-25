-- Accounting Service: V1__initial.sql
-- Double-entry bookkeeping: chart_of_accounts, journal_entries, journal_lines, account_balances

CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)     NOT NULL,
    code            VARCHAR(20)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    account_type    VARCHAR(20)     NOT NULL,  -- ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    balance_type    VARCHAR(10)     NOT NULL,  -- DEBIT, CREDIT
    parent_id       UUID            REFERENCES chart_of_accounts(id),
    description     VARCHAR(500),
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)     NOT NULL,
    reference       VARCHAR(100)    NOT NULL,
    description     VARCHAR(500),
    entry_date      DATE            NOT NULL DEFAULT CURRENT_DATE,
    status          VARCHAR(20)     NOT NULL DEFAULT 'POSTED',  -- DRAFT, POSTED, REVERSED
    source_event    VARCHAR(100),   -- e.g. "loan.disbursed", "payment.completed"
    source_id       VARCHAR(100),   -- e.g. application_id or payment_id
    total_debit     NUMERIC(18,2)   NOT NULL DEFAULT 0,
    total_credit    NUMERIC(18,2)   NOT NULL DEFAULT 0,
    posted_by       VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS journal_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id        UUID            NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(50)     NOT NULL,
    account_id      UUID            NOT NULL REFERENCES chart_of_accounts(id),
    line_no         INTEGER         NOT NULL,
    description     VARCHAR(300),
    debit_amount    NUMERIC(18,2)   NOT NULL DEFAULT 0,
    credit_amount   NUMERIC(18,2)   NOT NULL DEFAULT 0,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'KES'
);

CREATE TABLE IF NOT EXISTS account_balances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)     NOT NULL,
    account_id      UUID            NOT NULL REFERENCES chart_of_accounts(id),
    period_year     INTEGER         NOT NULL,
    period_month    INTEGER         NOT NULL,
    opening_balance NUMERIC(18,2)   NOT NULL DEFAULT 0,
    total_debits    NUMERIC(18,2)   NOT NULL DEFAULT 0,
    total_credits   NUMERIC(18,2)   NOT NULL DEFAULT 0,
    closing_balance NUMERIC(18,2)   NOT NULL DEFAULT 0,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'KES',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, account_id, period_year, period_month)
);

CREATE INDEX idx_coa_tenant        ON chart_of_accounts(tenant_id);
CREATE INDEX idx_coa_code          ON chart_of_accounts(tenant_id, code);
CREATE INDEX idx_coa_type          ON chart_of_accounts(account_type);
CREATE INDEX idx_je_tenant         ON journal_entries(tenant_id);
CREATE INDEX idx_je_date           ON journal_entries(entry_date);
CREATE INDEX idx_je_source         ON journal_entries(source_event, source_id);
CREATE INDEX idx_jl_entry          ON journal_lines(entry_id);
CREATE INDEX idx_jl_account        ON journal_lines(account_id);
CREATE INDEX idx_balances_account  ON account_balances(account_id);
CREATE INDEX idx_balances_period   ON account_balances(tenant_id, period_year, period_month);

-- Seed standard chart of accounts (system-level, tenant_id = 'system')
INSERT INTO chart_of_accounts (tenant_id, code, name, account_type, balance_type, description) VALUES
  ('system', '1000', 'Cash and Cash Equivalents',  'ASSET',     'DEBIT',  'Bank and M-Pesa float'),
  ('system', '1100', 'Loans Receivable',            'ASSET',     'DEBIT',  'Outstanding loan principal'),
  ('system', '1200', 'Interest Receivable',         'ASSET',     'DEBIT',  'Accrued interest on loans'),
  ('system', '1300', 'Fee Receivable',              'ASSET',     'DEBIT',  'Fees due from borrowers'),
  ('system', '1400', 'Loan Loss Provision',         'ASSET',     'CREDIT', 'Provision for bad debts (contra-asset)'),
  ('system', '2000', 'Customer Deposits',           'LIABILITY', 'CREDIT', 'Funds held for customers'),
  ('system', '2100', 'Borrowings',                  'LIABILITY', 'CREDIT', 'Lines of credit and float'),
  ('system', '3000', 'Retained Earnings',           'EQUITY',    'CREDIT', 'Accumulated profits'),
  ('system', '4000', 'Interest Income',             'INCOME',    'CREDIT', 'Interest earned on loans'),
  ('system', '4100', 'Fee Income',                  'INCOME',    'CREDIT', 'Origination and service fees'),
  ('system', '4200', 'Penalty Income',              'INCOME',    'CREDIT', 'Late payment penalties'),
  ('system', '5000', 'Interest Expense',            'EXPENSE',   'DEBIT',  'Cost of borrowing'),
  ('system', '5100', 'Loan Loss Expense',           'EXPENSE',   'DEBIT',  'Provision charge');
