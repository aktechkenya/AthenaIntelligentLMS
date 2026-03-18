DROP INDEX IF EXISTS idx_fraud_cases_sla;
ALTER TABLE fraud_cases DROP COLUMN IF EXISTS sla_breached;
ALTER TABLE fraud_cases DROP COLUMN IF EXISTS sla_deadline;
