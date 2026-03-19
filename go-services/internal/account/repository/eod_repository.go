package repository

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/athena-lms/go-services/internal/account/model"
)

// ─── EOD Runs ────────────────────────────────────────────────────────────────

// CreateEODRun inserts a new EOD run record. Returns conflict error if already run today.
func (r *Repository) CreateEODRun(ctx context.Context, run *model.EODRun) error {
	run.ID = uuid.New()
	run.StartedAt = time.Now()
	return r.pool.QueryRow(ctx,
		`INSERT INTO eod_runs (id, tenant_id, run_date, status, initiated_by, started_at)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING id`,
		run.ID, run.TenantID, run.RunDate, run.Status, run.InitiatedBy, run.StartedAt,
	).Scan(&run.ID)
}

// GetEODRunForDate returns the EOD run for a given date, if any.
func (r *Repository) GetEODRunForDate(ctx context.Context, tenantID string, date time.Time) (*model.EODRun, error) {
	run := &model.EODRun{}
	err := r.pool.QueryRow(ctx,
		`SELECT id, tenant_id, run_date, status, initiated_by, started_at, completed_at,
			accounts_accrued, accrual_errors, dormant_detected, dormancy_errors,
			matured_processed, maturity_errors, interest_posted, posting_errors, fees_applied,
			error_details, total_interest_accrued, total_interest_posted, total_wht_deducted
		FROM eod_runs WHERE tenant_id = $1 AND run_date = $2`,
		tenantID, date,
	).Scan(
		&run.ID, &run.TenantID, &run.RunDate, &run.Status, &run.InitiatedBy,
		&run.StartedAt, &run.CompletedAt,
		&run.AccountsAccrued, &run.AccrualErrors, &run.DormantDetected, &run.DormancyErrors,
		&run.MaturedProcessed, &run.MaturityErrors, &run.InterestPostedCount, &run.PostingErrors,
		&run.FeesApplied, &run.ErrorDetails,
		&run.TotalInterestAccrued, &run.TotalInterestPosted, &run.TotalWHTDeducted,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return run, nil
}

// UpdateEODRun saves the final state of an EOD run.
func (r *Repository) UpdateEODRun(ctx context.Context, run *model.EODRun) error {
	now := time.Now()
	run.CompletedAt = &now
	_, err := r.pool.Exec(ctx,
		`UPDATE eod_runs SET
			status = $1, completed_at = $2,
			accounts_accrued = $3, accrual_errors = $4,
			dormant_detected = $5, dormancy_errors = $6,
			matured_processed = $7, maturity_errors = $8,
			interest_posted = $9, posting_errors = $10,
			fees_applied = $11, error_details = $12,
			total_interest_accrued = $13, total_interest_posted = $14, total_wht_deducted = $15
		WHERE id = $16`,
		run.Status, run.CompletedAt,
		run.AccountsAccrued, run.AccrualErrors,
		run.DormantDetected, run.DormancyErrors,
		run.MaturedProcessed, run.MaturityErrors,
		run.InterestPostedCount, run.PostingErrors,
		run.FeesApplied, run.ErrorDetails,
		run.TotalInterestAccrued.String(), run.TotalInterestPosted.String(), run.TotalWHTDeducted.String(),
		run.ID,
	)
	return err
}

// ListEODRuns returns recent EOD runs for a tenant.
func (r *Repository) ListEODRuns(ctx context.Context, tenantID string, limit int) ([]*model.EODRun, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, tenant_id, run_date, status, initiated_by, started_at, completed_at,
			accounts_accrued, accrual_errors, dormant_detected, dormancy_errors,
			matured_processed, maturity_errors, interest_posted, posting_errors, fees_applied,
			error_details, total_interest_accrued, total_interest_posted, total_wht_deducted
		FROM eod_runs WHERE tenant_id = $1
		ORDER BY run_date DESC LIMIT $2`,
		tenantID, limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var runs []*model.EODRun
	for rows.Next() {
		run := &model.EODRun{}
		if err := rows.Scan(
			&run.ID, &run.TenantID, &run.RunDate, &run.Status, &run.InitiatedBy,
			&run.StartedAt, &run.CompletedAt,
			&run.AccountsAccrued, &run.AccrualErrors, &run.DormantDetected, &run.DormancyErrors,
			&run.MaturedProcessed, &run.MaturityErrors, &run.InterestPostedCount, &run.PostingErrors,
			&run.FeesApplied, &run.ErrorDetails,
			&run.TotalInterestAccrued, &run.TotalInterestPosted, &run.TotalWHTDeducted,
		); err != nil {
			return nil, err
		}
		runs = append(runs, run)
	}
	return runs, nil
}

// AcquireEODLock tries to acquire a PostgreSQL advisory lock for EOD processing.
// Returns true if the lock was acquired, false if another process holds it.
func (r *Repository) AcquireEODLock(ctx context.Context, tenantID string) (bool, error) {
	// Use a hash of tenantID as the lock key to avoid cross-tenant conflicts
	var acquired bool
	lockKey := int64(hashString(tenantID))
	err := r.pool.QueryRow(ctx,
		`SELECT pg_try_advisory_lock($1)`, lockKey,
	).Scan(&acquired)
	return acquired, err
}

// ReleaseEODLock releases the advisory lock.
func (r *Repository) ReleaseEODLock(ctx context.Context, tenantID string) error {
	lockKey := int64(hashString(tenantID))
	_, err := r.pool.Exec(ctx, `SELECT pg_advisory_unlock($1)`, lockKey)
	return err
}

// hashString produces a stable int hash for advisory lock keying.
func hashString(s string) uint32 {
	var h uint32 = 2166136261
	for i := 0; i < len(s); i++ {
		h ^= uint32(s[i])
		h *= 16777619
	}
	return h
}

// HasAccrualForDate checks if an accrual record already exists for an account+date.
func (r *Repository) HasAccrualForDate(ctx context.Context, accountID uuid.UUID, date time.Time) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM interest_accruals WHERE account_id = $1 AND accrual_date = $2)`,
		accountID, date,
	).Scan(&exists)
	return exists, err
}
