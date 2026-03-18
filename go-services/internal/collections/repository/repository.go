package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"

	"github.com/athena-lms/go-services/internal/collections/model"
)

// ---------- CollectionCaseRepository ----------

// CollectionCaseRepository provides data access for collection_cases.
type CollectionCaseRepository struct {
	pool *pgxpool.Pool
}

func NewCollectionCaseRepository(pool *pgxpool.Pool) *CollectionCaseRepository {
	return &CollectionCaseRepository{pool: pool}
}

func (r *CollectionCaseRepository) Save(ctx context.Context, c *model.CollectionCase) (*model.CollectionCase, error) {
	now := time.Now().UTC()
	if c.ID == uuid.Nil {
		c.ID = uuid.New()
		c.CreatedAt = now
		c.UpdatedAt = now
		if c.OpenedAt.IsZero() {
			c.OpenedAt = now
		}
		if c.Status == "" {
			c.Status = model.CaseStatusOpen
		}
		if c.Priority == "" {
			c.Priority = model.CasePriorityNormal
		}
		if c.CurrentStage == "" {
			c.CurrentStage = model.CollectionStageWatch
		}
		_, err := r.pool.Exec(ctx, `
			INSERT INTO collection_cases
				(id, tenant_id, loan_id, customer_id, case_number, status, priority,
				 current_dpd, current_stage, outstanding_amount, assigned_to, opened_at,
				 closed_at, last_action_at, notes, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)`,
			c.ID, c.TenantID, c.LoanID, c.CustomerID, c.CaseNumber,
			string(c.Status), string(c.Priority), c.CurrentDPD, string(c.CurrentStage),
			c.OutstandingAmount, c.AssignedTo, c.OpenedAt, c.ClosedAt, c.LastActionAt,
			c.Notes, c.CreatedAt, c.UpdatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("insert collection case: %w", err)
		}
		return c, nil
	}

	c.UpdatedAt = now
	_, err := r.pool.Exec(ctx, `
		UPDATE collection_cases SET
			tenant_id=$2, loan_id=$3, customer_id=$4, case_number=$5, status=$6,
			priority=$7, current_dpd=$8, current_stage=$9, outstanding_amount=$10,
			assigned_to=$11, opened_at=$12, closed_at=$13, last_action_at=$14,
			notes=$15, updated_at=$16
		WHERE id=$1`,
		c.ID, c.TenantID, c.LoanID, c.CustomerID, c.CaseNumber,
		string(c.Status), string(c.Priority), c.CurrentDPD, string(c.CurrentStage),
		c.OutstandingAmount, c.AssignedTo, c.OpenedAt, c.ClosedAt, c.LastActionAt,
		c.Notes, c.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("update collection case: %w", err)
	}
	return c, nil
}

func (r *CollectionCaseRepository) FindByLoanID(ctx context.Context, loanID uuid.UUID) (*model.CollectionCase, error) {
	return r.scanOne(ctx, `SELECT `+caseColumns+` FROM collection_cases WHERE loan_id=$1`, loanID)
}

func (r *CollectionCaseRepository) FindByTenantIDAndID(ctx context.Context, tenantID string, id uuid.UUID) (*model.CollectionCase, error) {
	return r.scanOne(ctx, `SELECT `+caseColumns+` FROM collection_cases WHERE tenant_id=$1 AND id=$2`, tenantID, id)
}

func (r *CollectionCaseRepository) FindByTenantID(ctx context.Context, tenantID string, offset, limit int) ([]*model.CollectionCase, error) {
	return r.scanMany(ctx, `SELECT `+caseColumns+` FROM collection_cases WHERE tenant_id=$1 ORDER BY created_at DESC LIMIT $2 OFFSET $3`, tenantID, limit, offset)
}

func (r *CollectionCaseRepository) FindByTenantIDAndStatus(ctx context.Context, tenantID string, status model.CaseStatus, offset, limit int) ([]*model.CollectionCase, error) {
	return r.scanMany(ctx, `SELECT `+caseColumns+` FROM collection_cases WHERE tenant_id=$1 AND status=$2 ORDER BY created_at DESC LIMIT $3 OFFSET $4`, tenantID, string(status), limit, offset)
}

func (r *CollectionCaseRepository) CountByTenantID(ctx context.Context, tenantID string) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM collection_cases WHERE tenant_id=$1`, tenantID).Scan(&count)
	return count, err
}

func (r *CollectionCaseRepository) CountByTenantIDAndStatus(ctx context.Context, tenantID string, status model.CaseStatus) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM collection_cases WHERE tenant_id=$1 AND status=$2`, tenantID, string(status)).Scan(&count)
	return count, err
}

func (r *CollectionCaseRepository) CountByTenantIDAndCurrentStage(ctx context.Context, tenantID string, stage model.CollectionStage) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM collection_cases WHERE tenant_id=$1 AND current_stage=$2`, tenantID, string(stage)).Scan(&count)
	return count, err
}

func (r *CollectionCaseRepository) CountByTenantIDAndPriority(ctx context.Context, tenantID string, priority model.CasePriority) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `SELECT COUNT(*) FROM collection_cases WHERE tenant_id=$1 AND priority=$2`, tenantID, string(priority)).Scan(&count)
	return count, err
}

const caseColumns = `id, tenant_id, loan_id, customer_id, case_number, status, priority,
	current_dpd, current_stage, outstanding_amount, assigned_to, opened_at,
	closed_at, last_action_at, notes, created_at, updated_at`

func scanCase(row pgx.Row) (*model.CollectionCase, error) {
	var c model.CollectionCase
	var status, priority, stage string
	var outstandingAmount decimal.Decimal
	err := row.Scan(
		&c.ID, &c.TenantID, &c.LoanID, &c.CustomerID, &c.CaseNumber,
		&status, &priority, &c.CurrentDPD, &stage, &outstandingAmount,
		&c.AssignedTo, &c.OpenedAt, &c.ClosedAt, &c.LastActionAt,
		&c.Notes, &c.CreatedAt, &c.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	c.Status = model.CaseStatus(status)
	c.Priority = model.CasePriority(priority)
	c.CurrentStage = model.CollectionStage(stage)
	c.OutstandingAmount = outstandingAmount
	return &c, nil
}

func (r *CollectionCaseRepository) scanOne(ctx context.Context, query string, args ...any) (*model.CollectionCase, error) {
	row := r.pool.QueryRow(ctx, query, args...)
	c, err := scanCase(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("query collection case: %w", err)
	}
	return c, nil
}

func (r *CollectionCaseRepository) scanMany(ctx context.Context, query string, args ...any) ([]*model.CollectionCase, error) {
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query collection cases: %w", err)
	}
	defer rows.Close()

	var results []*model.CollectionCase
	for rows.Next() {
		c, err := scanCase(rows)
		if err != nil {
			return nil, fmt.Errorf("scan collection case: %w", err)
		}
		results = append(results, c)
	}
	return results, rows.Err()
}

// ---------- CollectionActionRepository ----------

// CollectionActionRepository provides data access for collection_actions.
type CollectionActionRepository struct {
	pool *pgxpool.Pool
}

func NewCollectionActionRepository(pool *pgxpool.Pool) *CollectionActionRepository {
	return &CollectionActionRepository{pool: pool}
}

func (r *CollectionActionRepository) Save(ctx context.Context, a *model.CollectionAction) (*model.CollectionAction, error) {
	now := time.Now().UTC()
	if a.ID == uuid.Nil {
		a.ID = uuid.New()
		a.CreatedAt = now
		if a.PerformedAt.IsZero() {
			a.PerformedAt = now
		}
	}

	_, err := r.pool.Exec(ctx, `
		INSERT INTO collection_actions
			(id, tenant_id, case_id, action_type, outcome, notes,
			 contact_person, contact_method, performed_by, performed_at,
			 next_action_date, created_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
		ON CONFLICT (id) DO UPDATE SET
			action_type=$4, outcome=$5, notes=$6, contact_person=$7,
			contact_method=$8, performed_by=$9, performed_at=$10, next_action_date=$11`,
		a.ID, a.TenantID, a.CaseID, string(a.ActionType),
		nilableString(a.Outcome), a.Notes, a.ContactPerson, a.ContactMethod,
		a.PerformedBy, a.PerformedAt, a.NextActionDate, a.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("upsert collection action: %w", err)
	}
	return a, nil
}

func (r *CollectionActionRepository) FindByCaseIDOrderByPerformedAtDesc(ctx context.Context, caseID uuid.UUID) ([]*model.CollectionAction, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, case_id, action_type, outcome, notes,
			   contact_person, contact_method, performed_by, performed_at,
			   next_action_date, created_at
		FROM collection_actions
		WHERE case_id=$1
		ORDER BY performed_at DESC`, caseID)
	if err != nil {
		return nil, fmt.Errorf("query actions: %w", err)
	}
	defer rows.Close()

	var results []*model.CollectionAction
	for rows.Next() {
		var a model.CollectionAction
		var actionType string
		var outcome *string
		err := rows.Scan(
			&a.ID, &a.TenantID, &a.CaseID, &actionType, &outcome,
			&a.Notes, &a.ContactPerson, &a.ContactMethod, &a.PerformedBy,
			&a.PerformedAt, &a.NextActionDate, &a.CreatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("scan action: %w", err)
		}
		a.ActionType = model.ActionType(actionType)
		if outcome != nil {
			o := model.ActionOutcome(*outcome)
			a.Outcome = &o
		}
		results = append(results, &a)
	}
	return results, rows.Err()
}

// ---------- PtpRepository ----------

// PtpRepository provides data access for promises_to_pay.
type PtpRepository struct {
	pool *pgxpool.Pool
}

func NewPtpRepository(pool *pgxpool.Pool) *PtpRepository {
	return &PtpRepository{pool: pool}
}

func (r *PtpRepository) Save(ctx context.Context, p *model.PromiseToPay) (*model.PromiseToPay, error) {
	now := time.Now().UTC()
	if p.ID == uuid.Nil {
		p.ID = uuid.New()
		p.CreatedAt = now
		p.UpdatedAt = now
		if p.Status == "" {
			p.Status = model.PtpStatusPending
		}
		_, err := r.pool.Exec(ctx, `
			INSERT INTO promises_to_pay
				(id, tenant_id, case_id, promised_amount, promise_date, status,
				 notes, created_by, fulfilled_at, broken_at, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)`,
			p.ID, p.TenantID, p.CaseID, p.PromisedAmount, p.PromiseDate,
			string(p.Status), p.Notes, p.CreatedBy, p.FulfilledAt, p.BrokenAt,
			p.CreatedAt, p.UpdatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("insert ptp: %w", err)
		}
		return p, nil
	}

	p.UpdatedAt = now
	_, err := r.pool.Exec(ctx, `
		UPDATE promises_to_pay SET
			status=$2, notes=$3, fulfilled_at=$4, broken_at=$5, updated_at=$6
		WHERE id=$1`,
		p.ID, string(p.Status), p.Notes, p.FulfilledAt, p.BrokenAt, p.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("update ptp: %w", err)
	}
	return p, nil
}

func (r *PtpRepository) FindByCaseIDOrderByCreatedAtDesc(ctx context.Context, caseID uuid.UUID) ([]*model.PromiseToPay, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, case_id, promised_amount, promise_date, status,
			   notes, created_by, fulfilled_at, broken_at, created_at, updated_at
		FROM promises_to_pay
		WHERE case_id=$1
		ORDER BY created_at DESC`, caseID)
	if err != nil {
		return nil, fmt.Errorf("query ptps: %w", err)
	}
	defer rows.Close()
	return r.scanRows(rows)
}

func (r *PtpRepository) FindByStatusAndPromiseDateBefore(ctx context.Context, status model.PtpStatus, before time.Time) ([]*model.PromiseToPay, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, case_id, promised_amount, promise_date, status,
			   notes, created_by, fulfilled_at, broken_at, created_at, updated_at
		FROM promises_to_pay
		WHERE status=$1 AND promise_date < $2`, string(status), before)
	if err != nil {
		return nil, fmt.Errorf("query expired ptps: %w", err)
	}
	defer rows.Close()
	return r.scanRows(rows)
}

func (r *PtpRepository) scanRows(rows pgx.Rows) ([]*model.PromiseToPay, error) {
	var results []*model.PromiseToPay
	for rows.Next() {
		var p model.PromiseToPay
		var status string
		err := rows.Scan(
			&p.ID, &p.TenantID, &p.CaseID, &p.PromisedAmount, &p.PromiseDate,
			&status, &p.Notes, &p.CreatedBy, &p.FulfilledAt, &p.BrokenAt,
			&p.CreatedAt, &p.UpdatedAt,
		)
		if err != nil {
			return nil, fmt.Errorf("scan ptp: %w", err)
		}
		p.Status = model.PtpStatus(status)
		results = append(results, &p)
	}
	return results, rows.Err()
}

// nilableString converts *ActionOutcome to *string for DB storage.
func nilableString(o *model.ActionOutcome) *string {
	if o == nil {
		return nil
	}
	s := string(*o)
	return &s
}
