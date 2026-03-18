-- Seed demo wallet
INSERT INTO customer_wallets (tenant_id, customer_id, account_number, currency, current_balance, available_balance, status)
VALUES ('admin', 'CUST-DEMO', 'WLT-DEMO-001', 'KES', 0, 0, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- Seed default band configs (system-level)
INSERT INTO credit_band_configs (tenant_id, band, min_score, max_score, approved_limit, interest_rate, arrangement_fee, annual_fee) VALUES
    ('system', 'A', 750, 900, 100000, 0.1500, 1000, 500),
    ('system', 'B', 650, 749, 50000, 0.2000, 750, 500),
    ('system', 'C', 550, 649, 20000, 0.2500, 500, 300),
    ('system', 'D', 0, 549, 5000, 0.3000, 250, 200)
ON CONFLICT DO NOTHING;
