ALTER TABLE accounts
    DROP COLUMN IF EXISTS deposit_product_id,
    DROP COLUMN IF EXISTS branch_id,
    DROP COLUMN IF EXISTS opened_by,
    DROP COLUMN IF EXISTS closed_at,
    DROP COLUMN IF EXISTS closure_reason,
    DROP COLUMN IF EXISTS last_transaction_date,
    DROP COLUMN IF EXISTS dormant_since,
    DROP COLUMN IF EXISTS maturity_date,
    DROP COLUMN IF EXISTS term_days,
    DROP COLUMN IF EXISTS locked_amount,
    DROP COLUMN IF EXISTS auto_renew,
    DROP COLUMN IF EXISTS accrued_interest,
    DROP COLUMN IF EXISTS last_interest_accrual_date,
    DROP COLUMN IF EXISTS last_interest_posting_date,
    DROP COLUMN IF EXISTS interest_rate_override;

ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_account_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_account_type_check
    CHECK (account_type IN ('CURRENT','SAVINGS','WALLET'));
