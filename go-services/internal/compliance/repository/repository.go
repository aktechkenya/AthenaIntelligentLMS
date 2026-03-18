package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/athena-lms/go-services/internal/compliance/model"
)

// Repository provides data access for all compliance entities.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new Repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// ─── AML Alerts ─────────────────────────────────────────────────────────────

// CreateAlert inserts a new AML alert and returns it with generated fields.
func (r *Repository) CreateAlert(ctx context.Context, alert *model.AmlAlert) (*model.AmlAlert, error) {
	query := `
		INSERT INTO aml_alerts (tenant_id, alert_type, severity, status, subject_type, subject_id,
			customer_id, description, trigger_event, trigger_amount, sar_filed)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
		RETURNING id, created_at, updated_at`

	err := r.pool.QueryRow(ctx, query,
		alert.TenantID, alert.AlertType, alert.Severity, alert.Status,
		alert.SubjectType, alert.SubjectID, alert.CustomerID, alert.Description,
		alert.TriggerEvent, alert.TriggerAmount, alert.SarFiled,
	).Scan(&alert.ID, &alert.CreatedAt, &alert.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("create alert: %w", err)
	}
	return alert, nil
}

// GetAlertByID retrieves an alert by ID and tenant.
func (r *Repository) GetAlertByID(ctx context.Context, id uuid.UUID, tenantID string) (*model.AmlAlert, error) {
	query := `
		SELECT id, tenant_id, alert_type, severity, status, subject_type, subject_id,
			customer_id, description, trigger_event, trigger_amount, sar_filed, sar_reference,
			assigned_to, resolved_by, resolved_at, resolution_notes, created_at, updated_at
		FROM aml_alerts WHERE id = $1 AND tenant_id = $2`

	alert := &model.AmlAlert{}
	err := r.pool.QueryRow(ctx, query, id, tenantID).Scan(
		&alert.ID, &alert.TenantID, &alert.AlertType, &alert.Severity, &alert.Status,
		&alert.SubjectType, &alert.SubjectID, &alert.CustomerID, &alert.Description,
		&alert.TriggerEvent, &alert.TriggerAmount, &alert.SarFiled, &alert.SarReference,
		&alert.AssignedTo, &alert.ResolvedBy, &alert.ResolvedAt, &alert.ResolutionNotes,
		&alert.CreatedAt, &alert.UpdatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get alert by id: %w", err)
	}
	return alert, nil
}

// ListAlerts returns a paginated list of alerts, optionally filtered by status.
func (r *Repository) ListAlerts(ctx context.Context, tenantID string, status *model.AlertStatus, page, size int) ([]model.AmlAlert, int64, error) {
	offset := page * size

	// Count query
	countQuery := `SELECT COUNT(*) FROM aml_alerts WHERE tenant_id = $1`
	listQuery := `
		SELECT id, tenant_id, alert_type, severity, status, subject_type, subject_id,
			customer_id, description, trigger_event, trigger_amount, sar_filed, sar_reference,
			assigned_to, resolved_by, resolved_at, resolution_notes, created_at, updated_at
		FROM aml_alerts WHERE tenant_id = $1`

	args := []any{tenantID}

	if status != nil {
		countQuery += ` AND status = $2`
		listQuery += ` AND status = $2`
		args = append(args, *status)
	}

	listQuery += ` ORDER BY created_at DESC LIMIT $` + fmt.Sprintf("%d", len(args)+1) +
		` OFFSET $` + fmt.Sprintf("%d", len(args)+2)

	var total int64
	if err := r.pool.QueryRow(ctx, countQuery, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count alerts: %w", err)
	}

	args = append(args, size, offset)
	rows, err := r.pool.Query(ctx, listQuery, args...)
	if err != nil {
		return nil, 0, fmt.Errorf("list alerts: %w", err)
	}
	defer rows.Close()

	var alerts []model.AmlAlert
	for rows.Next() {
		var a model.AmlAlert
		if err := rows.Scan(
			&a.ID, &a.TenantID, &a.AlertType, &a.Severity, &a.Status,
			&a.SubjectType, &a.SubjectID, &a.CustomerID, &a.Description,
			&a.TriggerEvent, &a.TriggerAmount, &a.SarFiled, &a.SarReference,
			&a.AssignedTo, &a.ResolvedBy, &a.ResolvedAt, &a.ResolutionNotes,
			&a.CreatedAt, &a.UpdatedAt,
		); err != nil {
			return nil, 0, fmt.Errorf("scan alert: %w", err)
		}
		alerts = append(alerts, a)
	}
	return alerts, total, nil
}

// UpdateAlert updates an existing alert.
func (r *Repository) UpdateAlert(ctx context.Context, alert *model.AmlAlert) error {
	query := `
		UPDATE aml_alerts SET
			status = $1, sar_filed = $2, sar_reference = $3,
			resolved_by = $4, resolved_at = $5, resolution_notes = $6,
			updated_at = NOW()
		WHERE id = $7 AND tenant_id = $8`

	_, err := r.pool.Exec(ctx, query,
		alert.Status, alert.SarFiled, alert.SarReference,
		alert.ResolvedBy, alert.ResolvedAt, alert.ResolutionNotes,
		alert.ID, alert.TenantID,
	)
	if err != nil {
		return fmt.Errorf("update alert: %w", err)
	}
	return nil
}

// CountAlertsByStatus counts alerts by tenant and status.
func (r *Repository) CountAlertsByStatus(ctx context.Context, tenantID string, status model.AlertStatus) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM aml_alerts WHERE tenant_id = $1 AND status = $2`,
		tenantID, status,
	).Scan(&count)
	return count, err
}

// CountAlertsBySeverityAndStatus counts alerts by tenant, severity, and status.
func (r *Repository) CountAlertsBySeverityAndStatus(ctx context.Context, tenantID string, severity model.AlertSeverity, status model.AlertStatus) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM aml_alerts WHERE tenant_id = $1 AND severity = $2 AND status = $3`,
		tenantID, severity, status,
	).Scan(&count)
	return count, err
}

// ─── KYC Records ────────────────────────────────────────────────────────────

// GetKycByTenantAndCustomer retrieves a KYC record by tenant and customer ID.
func (r *Repository) GetKycByTenantAndCustomer(ctx context.Context, tenantID, customerID string) (*model.KycRecord, error) {
	query := `
		SELECT id, tenant_id, customer_id, status, check_type, national_id, full_name,
			phone, risk_level, failure_reason, checked_by, checked_at, expires_at,
			created_at, updated_at
		FROM kyc_records WHERE tenant_id = $1 AND customer_id = $2`

	rec := &model.KycRecord{}
	err := r.pool.QueryRow(ctx, query, tenantID, customerID).Scan(
		&rec.ID, &rec.TenantID, &rec.CustomerID, &rec.Status, &rec.CheckType,
		&rec.NationalID, &rec.FullName, &rec.Phone, &rec.RiskLevel, &rec.FailureReason,
		&rec.CheckedBy, &rec.CheckedAt, &rec.ExpiresAt, &rec.CreatedAt, &rec.UpdatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get kyc by customer: %w", err)
	}
	return rec, nil
}

// UpsertKyc inserts or updates a KYC record (upsert on tenant_id + customer_id).
func (r *Repository) UpsertKyc(ctx context.Context, rec *model.KycRecord) (*model.KycRecord, error) {
	query := `
		INSERT INTO kyc_records (tenant_id, customer_id, status, check_type, national_id,
			full_name, phone, risk_level)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		ON CONFLICT (tenant_id, customer_id) DO UPDATE SET
			status = EXCLUDED.status,
			check_type = EXCLUDED.check_type,
			national_id = EXCLUDED.national_id,
			full_name = EXCLUDED.full_name,
			phone = EXCLUDED.phone,
			risk_level = EXCLUDED.risk_level,
			updated_at = NOW()
		RETURNING id, created_at, updated_at`

	err := r.pool.QueryRow(ctx, query,
		rec.TenantID, rec.CustomerID, rec.Status, rec.CheckType,
		rec.NationalID, rec.FullName, rec.Phone, rec.RiskLevel,
	).Scan(&rec.ID, &rec.CreatedAt, &rec.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("upsert kyc: %w", err)
	}
	return rec, nil
}

// UpdateKyc updates a KYC record's mutable fields.
func (r *Repository) UpdateKyc(ctx context.Context, rec *model.KycRecord) error {
	query := `
		UPDATE kyc_records SET
			status = $1, failure_reason = $2, checked_at = $3, updated_at = NOW()
		WHERE id = $4 AND tenant_id = $5`

	_, err := r.pool.Exec(ctx, query,
		rec.Status, rec.FailureReason, rec.CheckedAt, rec.ID, rec.TenantID,
	)
	if err != nil {
		return fmt.Errorf("update kyc: %w", err)
	}
	return nil
}

// CountKycByStatus counts KYC records by tenant and status.
func (r *Repository) CountKycByStatus(ctx context.Context, tenantID string, status model.KycStatus) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM kyc_records WHERE tenant_id = $1 AND status = $2`,
		tenantID, status,
	).Scan(&count)
	return count, err
}

// ─── SAR Filings ────────────────────────────────────────────────────────────

// CreateSar inserts a new SAR filing.
func (r *Repository) CreateSar(ctx context.Context, sar *model.SarFiling) (*model.SarFiling, error) {
	query := `
		INSERT INTO sar_filings (tenant_id, alert_id, reference_number, filing_date,
			regulator, status, submitted_by, notes)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		RETURNING id, created_at`

	err := r.pool.QueryRow(ctx, query,
		sar.TenantID, sar.AlertID, sar.ReferenceNumber, sar.FilingDate,
		sar.Regulator, sar.Status, sar.SubmittedBy, sar.Notes,
	).Scan(&sar.ID, &sar.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("create sar: %w", err)
	}
	return sar, nil
}

// GetSarByAlertID retrieves a SAR filing by alert ID.
func (r *Repository) GetSarByAlertID(ctx context.Context, alertID uuid.UUID) (*model.SarFiling, error) {
	query := `
		SELECT id, tenant_id, alert_id, reference_number, filing_date, regulator,
			status, submitted_by, notes, created_at
		FROM sar_filings WHERE alert_id = $1`

	sar := &model.SarFiling{}
	err := r.pool.QueryRow(ctx, query, alertID).Scan(
		&sar.ID, &sar.TenantID, &sar.AlertID, &sar.ReferenceNumber,
		&sar.FilingDate, &sar.Regulator, &sar.Status, &sar.SubmittedBy,
		&sar.Notes, &sar.CreatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get sar by alert id: %w", err)
	}
	return sar, nil
}

// ─── Compliance Events ──────────────────────────────────────────────────────

// CreateEvent inserts a compliance event.
func (r *Repository) CreateEvent(ctx context.Context, evt *model.ComplianceEvent) (*model.ComplianceEvent, error) {
	query := `
		INSERT INTO compliance_events (tenant_id, event_type, source_service, subject_id, payload)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, created_at`

	err := r.pool.QueryRow(ctx, query,
		evt.TenantID, evt.EventType, evt.SourceService, evt.SubjectID, evt.Payload,
	).Scan(&evt.ID, &evt.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("create event: %w", err)
	}
	return evt, nil
}

// ListEvents returns a paginated list of compliance events ordered by created_at desc.
func (r *Repository) ListEvents(ctx context.Context, tenantID string, page, size int) ([]model.ComplianceEvent, int64, error) {
	offset := page * size

	var total int64
	if err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM compliance_events WHERE tenant_id = $1`, tenantID,
	).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count events: %w", err)
	}

	rows, err := r.pool.Query(ctx,
		`SELECT id, tenant_id, event_type, source_service, subject_id, payload, created_at
		 FROM compliance_events WHERE tenant_id = $1
		 ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
		tenantID, size, offset,
	)
	if err != nil {
		return nil, 0, fmt.Errorf("list events: %w", err)
	}
	defer rows.Close()

	var events []model.ComplianceEvent
	for rows.Next() {
		var e model.ComplianceEvent
		if err := rows.Scan(
			&e.ID, &e.TenantID, &e.EventType, &e.SourceService,
			&e.SubjectID, &e.Payload, &e.CreatedAt,
		); err != nil {
			return nil, 0, fmt.Errorf("scan event: %w", err)
		}
		events = append(events, e)
	}
	return events, total, nil
}

// Now returns the current time (useful for testing).
func Now() time.Time {
	return time.Now()
}
