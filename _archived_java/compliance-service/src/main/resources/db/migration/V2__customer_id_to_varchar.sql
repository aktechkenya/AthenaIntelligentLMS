ALTER TABLE kyc_records ALTER COLUMN customer_id TYPE VARCHAR(100) USING customer_id::text;
ALTER TABLE aml_alerts ALTER COLUMN customer_id TYPE VARCHAR(100) USING customer_id::text;
