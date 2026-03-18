ALTER TABLE collection_cases ALTER COLUMN customer_id TYPE VARCHAR(100) USING customer_id::text;
