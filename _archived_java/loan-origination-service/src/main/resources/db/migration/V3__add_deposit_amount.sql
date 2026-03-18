-- Track BNPL deposit amount on loan applications
ALTER TABLE loan_applications ADD COLUMN deposit_amount NUMERIC(18,2) DEFAULT 0;
