CREATE TABLE sar_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    report_number VARCHAR(30) NOT NULL UNIQUE,
    report_type VARCHAR(20) NOT NULL DEFAULT 'SAR',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    subject_customer_id VARCHAR(100),
    subject_name VARCHAR(300),
    subject_national_id VARCHAR(50),
    narrative TEXT,
    suspicious_amount DECIMAL(19,4),
    activity_start_date TIMESTAMPTZ,
    activity_end_date TIMESTAMPTZ,
    case_id UUID,
    prepared_by VARCHAR(100),
    reviewed_by VARCHAR(100),
    filed_by VARCHAR(100),
    filed_at TIMESTAMPTZ,
    filing_reference VARCHAR(100),
    regulator VARCHAR(100) DEFAULT 'FRC Kenya',
    filing_deadline TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sar_report_alert_ids (
    report_id UUID NOT NULL REFERENCES sar_reports(id) ON DELETE CASCADE,
    alert_id UUID NOT NULL,
    PRIMARY KEY (report_id, alert_id)
);

CREATE INDEX idx_sar_reports_tenant_status ON sar_reports(tenant_id, status);
CREATE INDEX idx_sar_reports_tenant_type ON sar_reports(tenant_id, report_type);
CREATE INDEX idx_sar_reports_tenant_customer ON sar_reports(tenant_id, subject_customer_id);
