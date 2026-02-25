ALTER TABLE payments ALTER COLUMN customer_id TYPE VARCHAR(100) USING customer_id::text;
ALTER TABLE payment_methods ALTER COLUMN customer_id TYPE VARCHAR(100) USING customer_id::text;
