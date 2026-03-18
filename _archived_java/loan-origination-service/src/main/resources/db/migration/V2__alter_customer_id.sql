-- Alter customer_id from UUID to VARCHAR(100) to support both numeric and UUID customer IDs
ALTER TABLE loan_applications ALTER COLUMN customer_id TYPE VARCHAR(100) USING customer_id::text;
