package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/athena-lms/go-services/internal/scoring/model"
)

// Repository provides data access for scoring entities.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new Repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// ---------- ScoringRequest ----------

const insertRequestSQL = `
INSERT INTO scoring_requests
    (tenant_id, loan_application_id, customer_id, status, trigger_event, requested_at, created_at, updated_at)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
RETURNING id`

// CreateRequest inserts a new ScoringRequest and returns the generated ID.
func (r *Repository) CreateRequest(ctx context.Context, req *model.ScoringRequest) (string, error) {
	now := time.Now().UTC()
	if req.RequestedAt.IsZero() {
		req.RequestedAt = now
	}
	if req.CreatedAt.IsZero() {
		req.CreatedAt = now
	}
	req.UpdatedAt = now
	if req.Status == "" {
		req.Status = model.ScoringStatusPending
	}

	var id string
	err := r.pool.QueryRow(ctx, insertRequestSQL,
		req.TenantID, req.LoanApplicationID, req.CustomerID,
		string(req.Status), req.TriggerEvent,
		req.RequestedAt, req.CreatedAt, req.UpdatedAt,
	).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("insert scoring request: %w", err)
	}
	req.ID = id
	return id, nil
}

const updateRequestSQL = `
UPDATE scoring_requests
SET status = $2, completed_at = $3, error_message = $4, updated_at = $5
WHERE id = $1`

// UpdateRequest updates status, completedAt, errorMessage, and updatedAt.
func (r *Repository) UpdateRequest(ctx context.Context, req *model.ScoringRequest) error {
	req.UpdatedAt = time.Now().UTC()
	_, err := r.pool.Exec(ctx, updateRequestSQL,
		req.ID, string(req.Status), req.CompletedAt, req.ErrorMessage, req.UpdatedAt,
	)
	if err != nil {
		return fmt.Errorf("update scoring request %s: %w", req.ID, err)
	}
	return nil
}

const findRequestByIDSQL = `
SELECT id, tenant_id, loan_application_id, customer_id, status, trigger_event,
       requested_at, completed_at, error_message, created_at, updated_at
FROM scoring_requests WHERE id = $1`

// FindRequestByID returns a ScoringRequest by primary key.
func (r *Repository) FindRequestByID(ctx context.Context, id string) (*model.ScoringRequest, error) {
	row := r.pool.QueryRow(ctx, findRequestByIDSQL, id)
	return scanRequest(row)
}

const findLatestRequestByLoanSQL = `
SELECT id, tenant_id, loan_application_id, customer_id, status, trigger_event,
       requested_at, completed_at, error_message, created_at, updated_at
FROM scoring_requests
WHERE loan_application_id = $1
ORDER BY created_at DESC
LIMIT 1`

// FindLatestRequestByLoan returns the most recent ScoringRequest for a loan application.
func (r *Repository) FindLatestRequestByLoan(ctx context.Context, loanApplicationID string) (*model.ScoringRequest, error) {
	row := r.pool.QueryRow(ctx, findLatestRequestByLoanSQL, loanApplicationID)
	return scanRequest(row)
}

const listRequestsByTenantSQL = `
SELECT id, tenant_id, loan_application_id, customer_id, status, trigger_event,
       requested_at, completed_at, error_message, created_at, updated_at
FROM scoring_requests
WHERE tenant_id = $1
ORDER BY created_at DESC
LIMIT $2 OFFSET $3`

const countRequestsByTenantSQL = `
SELECT COUNT(*) FROM scoring_requests WHERE tenant_id = $1`

// ListRequestsByTenant returns paginated ScoringRequests for a tenant.
func (r *Repository) ListRequestsByTenant(ctx context.Context, tenantID string, page, size int) ([]model.ScoringRequest, int64, error) {
	offset := page * size

	var total int64
	if err := r.pool.QueryRow(ctx, countRequestsByTenantSQL, tenantID).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count scoring requests: %w", err)
	}

	rows, err := r.pool.Query(ctx, listRequestsByTenantSQL, tenantID, size, offset)
	if err != nil {
		return nil, 0, fmt.Errorf("list scoring requests: %w", err)
	}
	defer rows.Close()

	var results []model.ScoringRequest
	for rows.Next() {
		req, err := scanRequestFromRows(rows)
		if err != nil {
			return nil, 0, err
		}
		results = append(results, *req)
	}
	return results, total, rows.Err()
}

func scanRequest(row pgx.Row) (*model.ScoringRequest, error) {
	var req model.ScoringRequest
	var status string
	err := row.Scan(
		&req.ID, &req.TenantID, &req.LoanApplicationID, &req.CustomerID,
		&status, &req.TriggerEvent,
		&req.RequestedAt, &req.CompletedAt, &req.ErrorMessage,
		&req.CreatedAt, &req.UpdatedAt,
	)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("scan scoring request: %w", err)
	}
	req.Status = model.ScoringStatus(status)
	return &req, nil
}

func scanRequestFromRows(rows pgx.Rows) (*model.ScoringRequest, error) {
	var req model.ScoringRequest
	var status string
	err := rows.Scan(
		&req.ID, &req.TenantID, &req.LoanApplicationID, &req.CustomerID,
		&status, &req.TriggerEvent,
		&req.RequestedAt, &req.CompletedAt, &req.ErrorMessage,
		&req.CreatedAt, &req.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("scan scoring request row: %w", err)
	}
	req.Status = model.ScoringStatus(status)
	return &req, nil
}

// ---------- ScoringResult ----------

const insertResultSQL = `
INSERT INTO scoring_results
    (tenant_id, request_id, loan_application_id, customer_id,
     base_score, crb_contribution, llm_adjustment, pd_probability,
     final_score, score_band, reasoning, llm_provider, llm_model,
     raw_response, scored_at, created_at)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
RETURNING id`

// CreateResult inserts a new ScoringResult and returns the generated ID.
func (r *Repository) CreateResult(ctx context.Context, res *model.ScoringResult) (string, error) {
	now := time.Now().UTC()
	if res.CreatedAt.IsZero() {
		res.CreatedAt = now
	}

	var id string
	err := r.pool.QueryRow(ctx, insertResultSQL,
		res.TenantID, res.RequestID, res.LoanApplicationID, res.CustomerID,
		res.BaseScore, res.CrbContribution, res.LlmAdjustment, res.PdProbability,
		res.FinalScore, res.ScoreBand, res.Reasoning, res.LlmProvider, res.LlmModel,
		res.RawResponse, res.ScoredAt, res.CreatedAt,
	).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("insert scoring result: %w", err)
	}
	res.ID = id
	return id, nil
}

const findResultByLoanSQL = `
SELECT id, tenant_id, request_id, loan_application_id, customer_id,
       base_score, crb_contribution, llm_adjustment, pd_probability,
       final_score, score_band, reasoning, llm_provider, llm_model,
       raw_response, scored_at, created_at
FROM scoring_results
WHERE loan_application_id = $1
ORDER BY created_at DESC
LIMIT 1`

// FindLatestResultByLoan returns the most recent ScoringResult for a loan application.
func (r *Repository) FindLatestResultByLoan(ctx context.Context, loanApplicationID string) (*model.ScoringResult, error) {
	row := r.pool.QueryRow(ctx, findResultByLoanSQL, loanApplicationID)
	return scanResult(row)
}

const findResultByCustomerSQL = `
SELECT id, tenant_id, request_id, loan_application_id, customer_id,
       base_score, crb_contribution, llm_adjustment, pd_probability,
       final_score, score_band, reasoning, llm_provider, llm_model,
       raw_response, scored_at, created_at
FROM scoring_results
WHERE customer_id = $1
ORDER BY created_at DESC
LIMIT 1`

// FindLatestResultByCustomer returns the most recent ScoringResult for a customer.
func (r *Repository) FindLatestResultByCustomer(ctx context.Context, customerID int64) (*model.ScoringResult, error) {
	row := r.pool.QueryRow(ctx, findResultByCustomerSQL, customerID)
	return scanResult(row)
}

const findResultByRequestSQL = `
SELECT id, tenant_id, request_id, loan_application_id, customer_id,
       base_score, crb_contribution, llm_adjustment, pd_probability,
       final_score, score_band, reasoning, llm_provider, llm_model,
       raw_response, scored_at, created_at
FROM scoring_results
WHERE request_id = $1`

// FindResultByRequest returns the ScoringResult for a given request.
func (r *Repository) FindResultByRequest(ctx context.Context, requestID string) (*model.ScoringResult, error) {
	row := r.pool.QueryRow(ctx, findResultByRequestSQL, requestID)
	return scanResult(row)
}

func scanResult(row pgx.Row) (*model.ScoringResult, error) {
	var res model.ScoringResult
	err := row.Scan(
		&res.ID, &res.TenantID, &res.RequestID, &res.LoanApplicationID, &res.CustomerID,
		&res.BaseScore, &res.CrbContribution, &res.LlmAdjustment, &res.PdProbability,
		&res.FinalScore, &res.ScoreBand, &res.Reasoning, &res.LlmProvider, &res.LlmModel,
		&res.RawResponse, &res.ScoredAt, &res.CreatedAt,
	)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("scan scoring result: %w", err)
	}
	return &res, nil
}
