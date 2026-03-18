DELETE FROM credit_band_configs WHERE tenant_id = 'system';
DELETE FROM customer_wallets WHERE tenant_id = 'admin' AND customer_id = 'CUST-DEMO';
