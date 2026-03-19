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
				 current_dpd, current_stage, outstanding_amount, assigned_to, product_type, opened_at,
				 closed_at, last_action_at, notes, write_off_reason, write_off_requested_by,
				 write_off_approved_by, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21)`,
			c.ID, c.TenantID, c.LoanID, c.CustomerID, c.CaseNumber,
			string(c.Status), string(c.Priority), c.CurrentDPD, string(c.CurrentStage),
			c.OutstandingAmount, c.AssignedTo, c.ProductType, c.OpenedAt, c.ClosedAt, c.LastActionAt,
			c.Notes, c.WriteOffReason, c.WriteOffRequestedBy, c.WriteOffApprovedBy,
			c.CreatedAt, c.UpdatedAt,
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
			assigned_to=$11, product_type=$12, opened_at=$13, closed_at=$14, last_action_at=$15,
			notes=$16, write_off_reason=$17, write_off_requested_by=$18,
			write_off_approved_by=$19, updated_at=$20
		WHERE id=$1`,
		c.ID, c.TenantID, c.LoanID, c.CustomerID, c.CaseNumber,
		string(c.Status), string(c.Priority), c.CurrentDPD, string(c.CurrentStage),
		c.OutstandingAmount, c.AssignedTo, c.ProductType, c.OpenedAt, c.ClosedAt, c.LastActionAt,
		c.Notes, c.WriteOffReason, c.WriteOffRequestedBy, c.WriteOffApprovedBy,
		c.UpdatedAt,
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

// CaseFilters holds filter criteria for case queries.
type CaseFilters struct {
	Status     *string
	Stage      *string
	Priority   *string
	AssignedTo *string
	MinDPD     *int
	MaxDPD     *int
	Search     *string // ILIKE on case_number or customer_id
}

// FindByFilters returns cases matching the given filters with sorting and pagination.
func (r *CollectionCaseRepository) FindByFilters(ctx context.Context, tenantID string, f CaseFilters, sort string, dir string, offset, limit int) ([]*model.CollectionCase, int64, error) {
	where := "WHERE tenant_id=$1"
	args := []any{tenantID}
	argIdx := 2

	if f.Status != nil {
		where += fmt.Sprintf(" AND status=$%d", argIdx)
		args = append(args, *f.Status)
		argIdx++
	}
	if f.Stage != nil {
		where += fmt.Sprintf(" AND current_stage=$%d", argIdx)
		args = append(args, *f.Stage)
		argIdx++
	}
	if f.Priority != nil {
		where += fmt.Sprintf(" AND priority=$%d", argIdx)
		args = append(args, *f.Priority)
		argIdx++
	}
	if f.AssignedTo != nil {
		where += fmt.Sprintf(" AND assigned_to=$%d", argIdx)
		args = append(args, *f.AssignedTo)
		argIdx++
	}
	if f.MinDPD != nil {
		where += fmt.Sprintf(" AND current_dpd >= $%d", argIdx)
		args = append(args, *f.MinDPD)
		argIdx++
	}
	if f.MaxDPD != nil {
		where += fmt.Sprintf(" AND current_dpd <= $%d", argIdx)
		args = append(args, *f.MaxDPD)
		argIdx++
	}
	if f.Search != nil {
		where += fmt.Sprintf(" AND (case_number ILIKE $%d OR customer_id ILIKE $%d)", argIdx, argIdx)
		args = append(args, "%"+*f.Search+"%")
		argIdx++
	}

	// Validate sort column
	allowedSorts := map[string]string{
		"current_dpd":        "current_dpd",
		"created_at":         "created_at",
		"outstanding_amount": "outstanding_amount",
		"current_stage":      "current_stage",
		"priority":           "priority",
	}
	sortCol, ok := allowedSorts[sort]
	if !ok {
		sortCol = "current_dpd"
	}
	sortDir := "DESC"
	if dir == "asc" || dir == "ASC" {
		sortDir = "ASC"
	}

	// Count query
	countQuery := "SELECT COUNT(*) FROM collection_cases " + where
	var total int64
	err := r.pool.QueryRow(ctx, countQuery, args...).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count filtered cases: %w", err)
	}

	// Data query
	dataQuery := fmt.Sprintf("SELECT %s FROM collection_cases %s ORDER BY %s %s LIMIT $%d OFFSET $%d",
		caseColumns, where, sortCol, sortDir, argIdx, argIdx+1)
	args = append(args, limit, offset)

	cases, err := r.scanMany(ctx, dataQuery, args...)
	if err != nil {
		return nil, 0, err
	}
	return cases, total, nil
}

// SumOutstandingByStage returns total outstanding amount grouped by stage for open cases.
func (r *CollectionCaseRepository) SumOutstandingByStage(ctx context.Context, tenantID string) (map[string]decimal.Decimal, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT current_stage, COALESCE(SUM(outstanding_amount), 0)
		FROM collection_cases
		WHERE tenant_id=$1 AND status NOT IN ('CLOSED','WRITTEN_OFF')
		GROUP BY current_stage`, tenantID)
	if err != nil {
		return nil, fmt.Errorf("sum outstanding by stage: %w", err)
	}
	defer rows.Close()

	result := make(map[string]decimal.Decimal)
	for rows.Next() {
		var stage string
		var amount decimal.Decimal
		if err := rows.Scan(&stage, &amount); err != nil {
			return nil, fmt.Errorf("scan stage amount: %w", err)
		}
		result[stage] = amount
	}
	return result, rows.Err()
}

// SumTotalOutstanding returns total outstanding amount for all open cases.
func (r *CollectionCaseRepository) SumTotalOutstanding(ctx context.Context, tenantID string) (decimal.Decimal, error) {
	var amount decimal.Decimal
	err := r.pool.QueryRow(ctx, `
		SELECT COALESCE(SUM(outstanding_amount), 0)
		FROM collection_cases
		WHERE tenant_id=$1 AND status NOT IN ('CLOSED','WRITTEN_OFF')`, tenantID).Scan(&amount)
	return amount, err
}

// FindOverdueFollowUps returns open/in-progress cases where the latest action's next_action_date is overdue.
func (r *CollectionCaseRepository) FindOverdueFollowUps(ctx context.Context, tenantID string) ([]*model.CollectionCase, error) {
	query := `SELECT ` + caseColumns + ` FROM collection_cases c
		WHERE c.tenant_id=$1
		  AND c.status NOT IN ('CLOSED','WRITTEN_OFF')
		  AND EXISTS (
		    SELECT 1 FROM collection_actions a
		    WHERE a.case_id = c.id
		      AND a.next_action_date < CURRENT_DATE
		      AND a.id = (
		        SELECT id FROM collection_actions
		        WHERE case_id = c.id
		        ORDER BY performed_at DESC
		        LIMIT 1
		      )
		  )`
	return r.scanMany(ctx, query, tenantID)
}

// FindAllOverdueFollowUps returns overdue follow-up cases across all tenants (for scheduler use).
func (r *CollectionCaseRepository) FindAllOverdueFollowUps(ctx context.Context) ([]*model.CollectionCase, error) {
	query := `SELECT ` + caseColumns + ` FROM collection_cases c
		WHERE c.status NOT IN ('CLOSED','WRITTEN_OFF')
		  AND EXISTS (
		    SELECT 1 FROM collection_actions a
		    WHERE a.case_id = c.id
		      AND a.next_action_date < CURRENT_DATE
		      AND a.id = (
		        SELECT id FROM collection_actions
		        WHERE case_id = c.id
		        ORDER BY performed_at DESC
		        LIMIT 1
		      )
		  )`
	return r.scanMany(ctx, query)
}

// FindLatestActionByCaseID returns the most recent action for a case.
func (r *CollectionActionRepository) FindLatestActionByCaseID(ctx context.Context, caseID uuid.UUID) (*model.CollectionAction, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, case_id, action_type, outcome, notes,
			   contact_person, contact_method, performed_by, performed_at,
			   next_action_date, created_at
		FROM collection_actions
		WHERE case_id=$1
		ORDER BY performed_at DESC
		LIMIT 1`, caseID)
	if err != nil {
		return nil, fmt.Errorf("query latest action: %w", err)
	}
	defer rows.Close()

	if !rows.Next() {
		return nil, nil
	}
	var a model.CollectionAction
	var actionType string
	var outcome *string
	err = rows.Scan(
		&a.ID, &a.TenantID, &a.CaseID, &actionType, &outcome,
		&a.Notes, &a.ContactPerson, &a.ContactMethod, &a.PerformedBy,
		&a.PerformedAt, &a.NextActionDate, &a.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("scan latest action: %w", err)
	}
	a.ActionType = model.ActionType(actionType)
	if outcome != nil {
		o := model.ActionOutcome(*outcome)
		a.Outcome = &o
	}
	return &a, nil
}

const caseColumns = `id, tenant_id, loan_id, customer_id, case_number, status, priority,
	current_dpd, current_stage, outstanding_amount, assigned_to, product_type, opened_at,
	closed_at, last_action_at, notes, write_off_reason, write_off_requested_by,
	write_off_approved_by, created_at, updated_at`

func scanCase(row pgx.Row) (*model.CollectionCase, error) {
	var c model.CollectionCase
	var status, priority, stage string
	var outstandingAmount decimal.Decimal
	err := row.Scan(
		&c.ID, &c.TenantID, &c.LoanID, &c.CustomerID, &c.CaseNumber,
		&status, &priority, &c.CurrentDPD, &stage, &outstandingAmount,
		&c.AssignedTo, &c.ProductType, &c.OpenedAt, &c.ClosedAt, &c.LastActionAt,
		&c.Notes, &c.WriteOffReason, &c.WriteOffRequestedBy, &c.WriteOffApprovedBy,
		&c.CreatedAt, &c.UpdatedAt,
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

// CountOverdueFollowUps counts cases where the latest action's next_action_date is overdue.
func (r *CollectionActionRepository) CountOverdueFollowUps(ctx context.Context, tenantID string) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT a.case_id)
		FROM collection_actions a
		JOIN collection_cases c ON a.case_id = c.id
		WHERE c.tenant_id=$1
		  AND c.status NOT IN ('CLOSED','WRITTEN_OFF')
		  AND a.next_action_date < CURRENT_DATE
		  AND a.id = (
		    SELECT id FROM collection_actions
		    WHERE case_id = a.case_id
		    ORDER BY performed_at DESC
		    LIMIT 1
		  )`, tenantID).Scan(&count)
	return count, err
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

// CountPendingByTenantID counts pending PTPs for a given tenant.
func (r *PtpRepository) CountPendingByTenantID(ctx context.Context, tenantID string) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `
		SELECT COUNT(*)
		FROM promises_to_pay p
		JOIN collection_cases c ON p.case_id = c.id
		WHERE c.tenant_id=$1 AND p.status='PENDING'`, tenantID).Scan(&count)
	return count, err
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

// FindPendingByCaseID returns all pending PTPs for a given case.
func (r *PtpRepository) FindPendingByCaseID(ctx context.Context, caseID uuid.UUID) ([]*model.PromiseToPay, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, tenant_id, case_id, promised_amount, promise_date, status,
			   notes, created_by, fulfilled_at, broken_at, created_at, updated_at
		FROM promises_to_pay
		WHERE case_id=$1 AND status='PENDING'
		ORDER BY promise_date ASC`, caseID)
	if err != nil {
		return nil, fmt.Errorf("query pending ptps: %w", err)
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

// CountOpenByAssignedTo returns the number of open/in-progress cases assigned to a given user.
func (r *CollectionCaseRepository) CountOpenByAssignedTo(ctx context.Context, tenantID, username string) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM collection_cases
		WHERE tenant_id=$1 AND assigned_to=$2
		  AND status NOT IN ('CLOSED','WRITTEN_OFF')`, tenantID, username).Scan(&count)
	return count, err
}

// nilableString converts *ActionOutcome to *string for DB storage.
func nilableString(o *model.ActionOutcome) *string {
	if o == nil {
		return nil
	}
	s := string(*o)
	return &s
}

// ---------- Phase 3: Bulk Operations ----------

// BulkAssign assigns multiple cases to a given officer.
func (r *CollectionCaseRepository) BulkAssign(ctx context.Context, tenantID string, caseIDs []uuid.UUID, assignedTo string) (int64, error) {
	now := time.Now().UTC()
	tag, err := r.pool.Exec(ctx, `
		UPDATE collection_cases SET assigned_to=$3, updated_at=$4
		WHERE tenant_id=$1 AND id = ANY($2)`,
		tenantID, caseIDs, assignedTo, now)
	if err != nil {
		return 0, fmt.Errorf("bulk assign: %w", err)
	}
	return tag.RowsAffected(), nil
}

// BulkPriority updates priority for multiple cases.
func (r *CollectionCaseRepository) BulkPriority(ctx context.Context, tenantID string, caseIDs []uuid.UUID, priority model.CasePriority) (int64, error) {
	now := time.Now().UTC()
	tag, err := r.pool.Exec(ctx, `
		UPDATE collection_cases SET priority=$3, updated_at=$4
		WHERE tenant_id=$1 AND id = ANY($2)`,
		tenantID, caseIDs, string(priority), now)
	if err != nil {
		return 0, fmt.Errorf("bulk priority: %w", err)
	}
	return tag.RowsAffected(), nil
}

// ---------- Phase 4: Analytics ----------

// GetStageAgeing returns count and total outstanding grouped by stage for open cases.
func (r *CollectionCaseRepository) GetStageAgeing(ctx context.Context, tenantID string) ([]model.StageAgeing, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT current_stage, COUNT(*), COALESCE(SUM(outstanding_amount), 0)
		FROM collection_cases
		WHERE tenant_id=$1 AND status NOT IN ('CLOSED','WRITTEN_OFF')
		GROUP BY current_stage
		ORDER BY current_stage`, tenantID)
	if err != nil {
		return nil, fmt.Errorf("stage ageing: %w", err)
	}
	defer rows.Close()

	var results []model.StageAgeing
	for rows.Next() {
		var sa model.StageAgeing
		if err := rows.Scan(&sa.Stage, &sa.Count, &sa.Amount); err != nil {
			return nil, fmt.Errorf("scan stage ageing: %w", err)
		}
		results = append(results, sa)
	}
	return results, rows.Err()
}

// CountNewCases returns the count of cases opened within the given date range.
func (r *CollectionCaseRepository) CountNewCases(ctx context.Context, tenantID string, from, to time.Time) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM collection_cases
		WHERE tenant_id=$1 AND opened_at >= $2 AND opened_at < $3`, tenantID, from, to).Scan(&count)
	return count, err
}

// CountClosedCases returns the count of cases closed within the given date range.
func (r *CollectionCaseRepository) CountClosedCases(ctx context.Context, tenantID string, from, to time.Time) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM collection_cases
		WHERE tenant_id=$1 AND closed_at >= $2 AND closed_at < $3`, tenantID, from, to).Scan(&count)
	return count, err
}

// AvgDPDOpenCases returns the average DPD for open cases.
func (r *CollectionCaseRepository) AvgDPDOpenCases(ctx context.Context, tenantID string) (float64, error) {
	var avg *float64
	err := r.pool.QueryRow(ctx, `
		SELECT AVG(current_dpd)::float8 FROM collection_cases
		WHERE tenant_id=$1 AND status NOT IN ('CLOSED','WRITTEN_OFF')`, tenantID).Scan(&avg)
	if err != nil {
		return 0, err
	}
	if avg == nil {
		return 0, nil
	}
	return *avg, nil
}

// GetRecoveryStats returns the total outstanding of cases closed in the period and
// total outstanding of all cases open at any point in the period.
func (r *CollectionCaseRepository) GetRecoveryStats(ctx context.Context, tenantID string, from, to time.Time) (closedAmount, totalAmount decimal.Decimal, err error) {
	err = r.pool.QueryRow(ctx, `
		SELECT COALESCE(SUM(outstanding_amount), 0)
		FROM collection_cases
		WHERE tenant_id=$1 AND closed_at >= $2 AND closed_at < $3`, tenantID, from, to).Scan(&closedAmount)
	if err != nil {
		return decimal.Zero, decimal.Zero, fmt.Errorf("recovery closed: %w", err)
	}
	err = r.pool.QueryRow(ctx, `
		SELECT COALESCE(SUM(outstanding_amount), 0)
		FROM collection_cases
		WHERE tenant_id=$1 AND opened_at < $3
		  AND (closed_at IS NULL OR closed_at >= $2)`, tenantID, from, to).Scan(&totalAmount)
	if err != nil {
		return decimal.Zero, decimal.Zero, fmt.Errorf("recovery total: %w", err)
	}
	return closedAmount, totalAmount, nil
}

// GetOfficerPerformance returns officer performance metrics within the given date range.
func (r *CollectionCaseRepository) GetOfficerPerformance(ctx context.Context, tenantID string, from, to time.Time) ([]model.OfficerPerformance, error) {
	rows, err := r.pool.Query(ctx, `
		WITH officers AS (
			SELECT DISTINCT assigned_to AS username
			FROM collection_cases
			WHERE tenant_id=$1 AND assigned_to IS NOT NULL
		),
		active AS (
			SELECT assigned_to, COUNT(*) AS cnt
			FROM collection_cases
			WHERE tenant_id=$1 AND status NOT IN ('CLOSED','WRITTEN_OFF') AND assigned_to IS NOT NULL
			GROUP BY assigned_to
		),
		actions AS (
			SELECT c.assigned_to, COUNT(a.id) AS cnt
			FROM collection_actions a
			JOIN collection_cases c ON a.case_id = c.id
			WHERE c.tenant_id=$1 AND a.performed_at >= $2 AND a.performed_at < $3
			  AND c.assigned_to IS NOT NULL
			GROUP BY c.assigned_to
		),
		ptps AS (
			SELECT c.assigned_to,
				COUNT(p.id) AS created,
				COUNT(p.id) FILTER (WHERE p.status='FULFILLED') AS fulfilled
			FROM promises_to_pay p
			JOIN collection_cases c ON p.case_id = c.id
			WHERE c.tenant_id=$1 AND p.created_at >= $2 AND p.created_at < $3
			  AND c.assigned_to IS NOT NULL
			GROUP BY c.assigned_to
		),
		closed AS (
			SELECT assigned_to, COUNT(*) AS cnt,
				AVG(EXTRACT(EPOCH FROM (closed_at - opened_at)) / 86400.0) AS avg_days
			FROM collection_cases
			WHERE tenant_id=$1 AND closed_at >= $2 AND closed_at < $3 AND assigned_to IS NOT NULL
			GROUP BY assigned_to
		)
		SELECT o.username,
			COALESCE(act.cnt, 0),
			COALESCE(ac.cnt, 0),
			COALESCE(pt.created, 0),
			COALESCE(pt.fulfilled, 0),
			COALESCE(cl.cnt, 0),
			COALESCE(cl.avg_days, 0)
		FROM officers o
		LEFT JOIN active act ON o.username = act.assigned_to
		LEFT JOIN actions ac ON o.username = ac.assigned_to
		LEFT JOIN ptps pt ON o.username = pt.assigned_to
		LEFT JOIN closed cl ON o.username = cl.assigned_to
		ORDER BY o.username`,
		tenantID, from, to)
	if err != nil {
		return nil, fmt.Errorf("officer performance: %w", err)
	}
	defer rows.Close()

	var results []model.OfficerPerformance
	for rows.Next() {
		var op model.OfficerPerformance
		if err := rows.Scan(&op.Username, &op.ActiveCases, &op.ActionsCount,
			&op.PtpsCreated, &op.PtpsFulfilled, &op.CasesClosed, &op.AvgResolutionDays); err != nil {
			return nil, fmt.Errorf("scan officer performance: %w", err)
		}
		results = append(results, op)
	}
	return results, rows.Err()
}

// GetAgeingReport returns cases grouped by DPD buckets.
func (r *CollectionCaseRepository) GetAgeingReport(ctx context.Context, tenantID string) ([]model.AgeingBucket, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT
			CASE
				WHEN current_dpd BETWEEN 1 AND 30 THEN '1-30'
				WHEN current_dpd BETWEEN 31 AND 60 THEN '31-60'
				WHEN current_dpd BETWEEN 61 AND 90 THEN '61-90'
				ELSE '90+'
			END AS bucket,
			COUNT(*),
			COALESCE(SUM(outstanding_amount), 0),
			product_type
		FROM collection_cases
		WHERE tenant_id=$1 AND status NOT IN ('CLOSED','WRITTEN_OFF')
		GROUP BY bucket, product_type
		ORDER BY bucket, product_type`, tenantID)
	if err != nil {
		return nil, fmt.Errorf("ageing report: %w", err)
	}
	defer rows.Close()

	var results []model.AgeingBucket
	for rows.Next() {
		var ab model.AgeingBucket
		if err := rows.Scan(&ab.Bucket, &ab.Count, &ab.Amount, &ab.ProductType); err != nil {
			return nil, fmt.Errorf("scan ageing bucket: %w", err)
		}
		results = append(results, ab)
	}
	return results, rows.Err()
}

// GetFulfilmentStats returns PTP fulfilment statistics for the given date range.
func (r *PtpRepository) GetFulfilmentStats(ctx context.Context, tenantID string, from, to time.Time) (fulfilled, total int64, err error) {
	err = r.pool.QueryRow(ctx, `
		SELECT
			COUNT(*) FILTER (WHERE p.status = 'FULFILLED') AS fulfilled,
			COUNT(*) AS total
		FROM promises_to_pay p
		JOIN collection_cases c ON p.case_id = c.id
		WHERE c.tenant_id=$1 AND p.created_at >= $2 AND p.created_at < $3`,
		tenantID, from, to).Scan(&fulfilled, &total)
	return
}
