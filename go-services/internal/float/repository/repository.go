package repository

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"

	"github.com/athena-lms/go-services/internal/float/model"
)

// Repository handles all float persistence operations using pgx.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new Repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// ---------- FloatAccount ----------

const accountColumns = `id, tenant_id, account_name, account_code, currency,
    float_limit, drawn_amount, available, status, description, created_at, updated_at`

func scanAccount(row pgx.Row) (*model.FloatAccount, error) {
	var a model.FloatAccount
	err := row.Scan(
		&a.ID, &a.TenantID, &a.AccountName, &a.AccountCode, &a.Currency,
		&a.FloatLimit, &a.DrawnAmount, &a.Available, &a.Status,
		&a.Description, &a.CreatedAt, &a.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &a, nil
}

func scanAccounts(rows pgx.Rows) ([]model.FloatAccount, error) {
	var result []model.FloatAccount
	for rows.Next() {
		var a model.FloatAccount
		if err := rows.Scan(
			&a.ID, &a.TenantID, &a.AccountName, &a.AccountCode, &a.Currency,
			&a.FloatLimit, &a.DrawnAmount, &a.Available, &a.Status,
			&a.Description, &a.CreatedAt, &a.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan account: %w", err)
		}
		result = append(result, a)
	}
	return result, nil
}

// FindAccountByTenantAndID returns a float account by tenant and ID.
func (r *Repository) FindAccountByTenantAndID(ctx context.Context, tenantID string, id uuid.UUID) (*model.FloatAccount, error) {
	row := r.pool.QueryRow(ctx, fmt.Sprintf(
		`SELECT %s FROM float_accounts WHERE tenant_id = $1 AND id = $2`, accountColumns),
		tenantID, id)
	a, err := scanAccount(row)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("find account by tenant and id: %w", err)
	}
	return a, nil
}

// FindAccountsByTenant returns all float accounts for a tenant.
func (r *Repository) FindAccountsByTenant(ctx context.Context, tenantID string) ([]model.FloatAccount, error) {
	rows, err := r.pool.Query(ctx, fmt.Sprintf(
		`SELECT %s FROM float_accounts WHERE tenant_id = $1 ORDER BY created_at`, accountColumns),
		tenantID)
	if err != nil {
		return nil, fmt.Errorf("find accounts by tenant: %w", err)
	}
	defer rows.Close()
	return scanAccounts(rows)
}

// ExistsAccountByTenantAndCode checks if an account with the given code exists for the tenant.
func (r *Repository) ExistsAccountByTenantAndCode(ctx context.Context, tenantID, accountCode string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM float_accounts WHERE tenant_id = $1 AND account_code = $2)`,
		tenantID, accountCode).Scan(&exists)
	return exists, err
}

// InsertAccount creates a new float account and returns it with DB-generated fields.
func (r *Repository) InsertAccount(ctx context.Context, a *model.FloatAccount) (*model.FloatAccount, error) {
	row := r.pool.QueryRow(ctx, fmt.Sprintf(`
		INSERT INTO float_accounts (tenant_id, account_name, account_code, currency, float_limit, drawn_amount, status, description)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		RETURNING %s`, accountColumns),
		a.TenantID, a.AccountName, a.AccountCode, a.Currency,
		a.FloatLimit, a.DrawnAmount, a.Status, a.Description,
	)
	return scanAccount(row)
}

// UpdateAccountDrawnAmount updates the drawn_amount and updated_at of a float account.
func (r *Repository) UpdateAccountDrawnAmount(ctx context.Context, id uuid.UUID, drawnAmount decimal.Decimal) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE float_accounts SET drawn_amount = $1, updated_at = NOW() WHERE id = $2`,
		drawnAmount, id)
	if err != nil {
		return fmt.Errorf("update account drawn amount: %w", err)
	}
	return nil
}

// ---------- FloatTransaction ----------

const txColumns = `id, tenant_id, float_account_id, transaction_type, amount,
    balance_before, balance_after, reference_id, reference_type, narration, event_id, created_at`

func scanTransaction(row pgx.Row) (*model.FloatTransaction, error) {
	var t model.FloatTransaction
	err := row.Scan(
		&t.ID, &t.TenantID, &t.FloatAccountID, &t.TransactionType, &t.Amount,
		&t.BalanceBefore, &t.BalanceAfter, &t.ReferenceID, &t.ReferenceType,
		&t.Narration, &t.EventID, &t.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &t, nil
}

// InsertTransaction creates a new float transaction.
func (r *Repository) InsertTransaction(ctx context.Context, t *model.FloatTransaction) (*model.FloatTransaction, error) {
	row := r.pool.QueryRow(ctx, fmt.Sprintf(`
		INSERT INTO float_transactions
			(tenant_id, float_account_id, transaction_type, amount, balance_before, balance_after,
			 reference_id, reference_type, narration, event_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		RETURNING %s`, txColumns),
		t.TenantID, t.FloatAccountID, t.TransactionType, t.Amount,
		t.BalanceBefore, t.BalanceAfter, t.ReferenceID, t.ReferenceType,
		t.Narration, t.EventID,
	)
	return scanTransaction(row)
}

// ExistsTransactionByEventID checks if a transaction with the given event_id already exists.
func (r *Repository) ExistsTransactionByEventID(ctx context.Context, eventID string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM float_transactions WHERE event_id = $1)`,
		eventID).Scan(&exists)
	return exists, err
}

// FindTransactionsByAccount returns paginated transactions for a float account, ordered by created_at desc.
func (r *Repository) FindTransactionsByAccount(ctx context.Context, accountID uuid.UUID, tenantID string, page, size int) ([]model.FloatTransaction, int64, error) {
	var total int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM float_transactions WHERE float_account_id = $1 AND tenant_id = $2`,
		accountID, tenantID).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count transactions: %w", err)
	}

	rows, err := r.pool.Query(ctx, fmt.Sprintf(`
		SELECT %s FROM float_transactions
		WHERE float_account_id = $1 AND tenant_id = $2
		ORDER BY created_at DESC
		LIMIT $3 OFFSET $4`, txColumns),
		accountID, tenantID, size, page*size)
	if err != nil {
		return nil, 0, fmt.Errorf("find transactions: %w", err)
	}
	defer rows.Close()

	var txs []model.FloatTransaction
	for rows.Next() {
		var t model.FloatTransaction
		if err := rows.Scan(
			&t.ID, &t.TenantID, &t.FloatAccountID, &t.TransactionType, &t.Amount,
			&t.BalanceBefore, &t.BalanceAfter, &t.ReferenceID, &t.ReferenceType,
			&t.Narration, &t.EventID, &t.CreatedAt,
		); err != nil {
			return nil, 0, fmt.Errorf("scan transaction: %w", err)
		}
		txs = append(txs, t)
	}
	return txs, total, nil
}

// ---------- FloatAllocation ----------

const allocColumns = `id, tenant_id, float_account_id, loan_id, allocated_amount,
    repaid_amount, outstanding, status, disbursed_at, closed_at, created_at`

// FindAllocationByLoanID returns the allocation for a specific loan, or nil if not found.
func (r *Repository) FindAllocationByLoanID(ctx context.Context, loanID uuid.UUID) (*model.FloatAllocation, error) {
	row := r.pool.QueryRow(ctx, fmt.Sprintf(
		`SELECT %s FROM float_allocations WHERE loan_id = $1`, allocColumns),
		loanID)
	var a model.FloatAllocation
	err := row.Scan(
		&a.ID, &a.TenantID, &a.FloatAccountID, &a.LoanID, &a.AllocatedAmount,
		&a.RepaidAmount, &a.Outstanding, &a.Status, &a.DisbursedAt, &a.ClosedAt, &a.CreatedAt,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("find allocation by loan id: %w", err)
	}
	return &a, nil
}

// FindAllocationsByTenant returns all allocations for a tenant.
func (r *Repository) FindAllocationsByTenant(ctx context.Context, tenantID string) ([]model.FloatAllocation, error) {
	rows, err := r.pool.Query(ctx, fmt.Sprintf(
		`SELECT %s FROM float_allocations WHERE tenant_id = $1`, allocColumns),
		tenantID)
	if err != nil {
		return nil, fmt.Errorf("find allocations by tenant: %w", err)
	}
	defer rows.Close()

	var result []model.FloatAllocation
	for rows.Next() {
		var a model.FloatAllocation
		if err := rows.Scan(
			&a.ID, &a.TenantID, &a.FloatAccountID, &a.LoanID, &a.AllocatedAmount,
			&a.RepaidAmount, &a.Outstanding, &a.Status, &a.DisbursedAt, &a.ClosedAt, &a.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan allocation: %w", err)
		}
		result = append(result, a)
	}
	return result, nil
}

// InsertAllocation creates a new float allocation.
func (r *Repository) InsertAllocation(ctx context.Context, a *model.FloatAllocation) (*model.FloatAllocation, error) {
	row := r.pool.QueryRow(ctx, fmt.Sprintf(`
		INSERT INTO float_allocations
			(tenant_id, float_account_id, loan_id, allocated_amount, repaid_amount, status, disbursed_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		RETURNING %s`, allocColumns),
		a.TenantID, a.FloatAccountID, a.LoanID, a.AllocatedAmount,
		a.RepaidAmount, a.Status, a.DisbursedAt,
	)
	var out model.FloatAllocation
	err := row.Scan(
		&out.ID, &out.TenantID, &out.FloatAccountID, &out.LoanID, &out.AllocatedAmount,
		&out.RepaidAmount, &out.Outstanding, &out.Status, &out.DisbursedAt, &out.ClosedAt, &out.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("insert allocation: %w", err)
	}
	return &out, nil
}
