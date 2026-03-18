CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- All LMS domain events stored for reporting
CREATE TABLE report_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    event_id        VARCHAR(100),
    event_type      VARCHAR(100) NOT NULL,
    event_category  VARCHAR(50),
    source_service  VARCHAR(100),
    subject_id      VARCHAR(100),
    customer_id     BIGINT,
    amount          NUMERIC(19,4),
    currency        VARCHAR(3) DEFAULT 'KES',
    payload         TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Daily portfolio snapshots
CREATE TABLE portfolio_snapshots (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    snapshot_date   DATE NOT NULL,
    period          VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    total_loans     INT NOT NULL DEFAULT 0,
    active_loans    INT NOT NULL DEFAULT 0,
    closed_loans    INT NOT NULL DEFAULT 0,
    defaulted_loans INT NOT NULL DEFAULT 0,
    total_disbursed NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_outstanding NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_collected NUMERIC(19,4) NOT NULL DEFAULT 0,
    watch_loans     INT NOT NULL DEFAULT 0,
    substandard_loans INT NOT NULL DEFAULT 0,
    doubtful_loans  INT NOT NULL DEFAULT 0,
    loss_loans      INT NOT NULL DEFAULT 0,
    par30           NUMERIC(19,4) NOT NULL DEFAULT 0,
    par90           NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_snapshot_date_tenant UNIQUE (tenant_id, snapshot_date, period)
);

-- Metric counters by event type per day
CREATE TABLE event_metrics (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    metric_date     DATE NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_count     BIGINT NOT NULL DEFAULT 0,
    total_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_metric_date_type UNIQUE (tenant_id, metric_date, event_type)
);

CREATE INDEX idx_report_events_tenant ON report_events(tenant_id);
CREATE INDEX idx_report_events_type ON report_events(event_type);
CREATE INDEX idx_report_events_occurred ON report_events(occurred_at);
CREATE INDEX idx_report_events_customer ON report_events(customer_id);
CREATE INDEX idx_snapshots_tenant_date ON portfolio_snapshots(tenant_id, snapshot_date DESC);
CREATE INDEX idx_metrics_date ON event_metrics(metric_date, tenant_id);
