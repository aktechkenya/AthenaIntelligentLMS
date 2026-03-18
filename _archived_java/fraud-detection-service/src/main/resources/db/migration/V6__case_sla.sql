ALTER TABLE fraud_cases ADD COLUMN sla_deadline TIMESTAMPTZ;
ALTER TABLE fraud_cases ADD COLUMN sla_breached BOOLEAN DEFAULT false;
CREATE INDEX idx_fraud_cases_sla ON fraud_cases(sla_breached, sla_deadline);
