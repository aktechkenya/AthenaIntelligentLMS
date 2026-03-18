CREATE TABLE scoring_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100),
    amount NUMERIC(19,4),
    ml_score DOUBLE PRECISION NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    model_available BOOLEAN DEFAULT true,
    latency_ms DOUBLE PRECISION,
    rule_score DOUBLE PRECISION,
    anomaly_score DOUBLE PRECISION,
    lgbm_score DOUBLE PRECISION,
    model_details TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_scoring_history_customer ON scoring_history(tenant_id, customer_id);
CREATE INDEX idx_scoring_history_created ON scoring_history(created_at);
CREATE INDEX idx_scoring_history_risk ON scoring_history(tenant_id, risk_level);
