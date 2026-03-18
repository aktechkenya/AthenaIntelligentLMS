-- Overdraft-specific GL accounts
INSERT INTO chart_of_accounts (tenant_id, code, name, account_type, balance_type, description) VALUES
    ('system', '1250', 'Overdraft Receivable', 'ASSET', 'DEBIT', 'Outstanding overdraft principal and interest'),
    ('system', '4300', 'Overdraft Interest Income', 'INCOME', 'CREDIT', 'Interest earned on overdraft facilities')
ON CONFLICT (tenant_id, code) DO NOTHING;
