package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"

	"github.com/athena-lms/go-services/internal/accounting/model"
)

// Repository provides database access for the accounting service.
type Repository struct {
	pool *pgxpool.Pool
}

// New creates a new accounting repository.
func New(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// --- Chart of Accounts ---

// FindAccountByTenantAndCode finds an account by tenant and code.
func (r *Repository) FindAccountByTenantAndCode(ctx context.Context, tenantID, code string) (*model.ChartOfAccount, error) {
	query := `SELECT id, tenant_id, code, name, account_type, balance_type, parent_id, description, is_active, created_at, updated_at
		FROM chart_of_accounts WHERE tenant_id = $1 AND code = $2`
	return r.scanAccount(ctx, query, tenantID, code)
}

// FindAccountByCodeAndTenantIn finds an account by code, searching in multiple tenant IDs (tenant first, then system).
func (r *Repository) FindAccountByCodeAndTenantIn(ctx context.Context, code string, tenantIDs []string) (*model.ChartOfAccount, error) {
	query := `SELECT id, tenant_id, code, name, account_type, balance_type, parent_id, description, is_active, created_at, updated_at
		FROM chart_of_accounts WHERE code = $1 AND tenant_id = ANY($2) ORDER BY CASE WHEN tenant_id = 'system' THEN 1 ELSE 0 END LIMIT 1`
	return r.scanAccount(ctx, query, code, tenantIDs)
}

// FindAccountByIDAndTenantIn finds an account by ID, searching in multiple tenant IDs.
func (r *Repository) FindAccountByIDAndTenantIn(ctx context.Context, id uuid.UUID, tenantIDs []string) (*model.ChartOfAccount, error) {
	query := `SELECT id, tenant_id, code, name, account_type, balance_type, parent_id, description, is_active, created_at, updated_at
		FROM chart_of_accounts WHERE id = $1 AND tenant_id = ANY($2)`
	return r.scanAccount(ctx, query, id, tenantIDs)
}

// ListActiveAccounts returns all active accounts for a tenant.
func (r *Repository) ListActiveAccounts(ctx context.Context, tenantID string) ([]model.ChartOfAccount, error) {
	query := `SELECT id, tenant_id, code, name, account_type, balance_type, parent_id, description, is_active, created_at, updated_at
		FROM chart_of_accounts WHERE tenant_id = $1 AND is_active = true ORDER BY code`
	return r.scanAccounts(ctx, query, tenantID)
}

// ListActiveAccountsByType returns all active accounts for a tenant and account type.
func (r *Repository) ListActiveAccountsByType(ctx context.Context, tenantID string, accountType model.AccountType) ([]model.ChartOfAccount, error) {
	query := `SELECT id, tenant_id, code, name, account_type, balance_type, parent_id, description, is_active, created_at, updated_at
		FROM chart_of_accounts WHERE tenant_id = $1 AND account_type = $2 AND is_active = true ORDER BY code`
	return r.scanAccounts(ctx, query, tenantID, string(accountType))
}

// CreateAccount inserts a new chart of accounts entry.
func (r *Repository) CreateAccount(ctx context.Context, a *model.ChartOfAccount) error {
	a.ID = uuid.New()
	now := time.Now()
	a.CreatedAt = now
	a.UpdatedAt = now
	_, err := r.pool.Exec(ctx,
		`INSERT INTO chart_of_accounts (id, tenant_id, code, name, account_type, balance_type, parent_id, description, is_active, created_at, updated_at)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
		a.ID, a.TenantID, a.Code, a.Name, string(a.AccountType), string(a.BalanceType),
		a.ParentID, a.Description, a.IsActive, a.CreatedAt, a.UpdatedAt)
	return err
}

// --- Journal Entries ---

// CreateJournalEntry inserts a journal entry and its lines within a transaction.
func (r *Repository) CreateJournalEntry(ctx context.Context, entry *model.JournalEntry) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	entry.ID = uuid.New()
	now := time.Now()
	entry.CreatedAt = now
	entry.UpdatedAt = now

	_, err = tx.Exec(ctx,
		`INSERT INTO journal_entries (id, tenant_id, reference, description, entry_date, status, source_event, source_id, total_debit, total_credit, posted_by, created_at, updated_at)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
		entry.ID, entry.TenantID, entry.Reference, entry.Description, entry.EntryDate,
		string(entry.Status), entry.SourceEvent, entry.SourceID,
		entry.TotalDebit, entry.TotalCredit, entry.PostedBy, entry.CreatedAt, entry.UpdatedAt)
	if err != nil {
		return fmt.Errorf("insert journal entry: %w", err)
	}

	for i := range entry.Lines {
		line := &entry.Lines[i]
		line.ID = uuid.New()
		line.EntryID = entry.ID
		line.TenantID = entry.TenantID
		if line.Currency == "" {
			line.Currency = "KES"
		}
		_, err = tx.Exec(ctx,
			`INSERT INTO journal_lines (id, entry_id, tenant_id, account_id, line_no, description, debit_amount, credit_amount, currency)
			 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
			line.ID, line.EntryID, line.TenantID, line.AccountID, line.LineNo,
			line.Description, line.DebitAmount, line.CreditAmount, line.Currency)
		if err != nil {
			return fmt.Errorf("insert journal line %d: %w", line.LineNo, err)
		}
	}

	// Update account balances
	year := entry.EntryDate.Year()
	month := int(entry.EntryDate.Month())
	for _, line := range entry.Lines {
		_, err = tx.Exec(ctx,
			`INSERT INTO account_balances (id, tenant_id, account_id, period_year, period_month, opening_balance, total_debits, total_credits, closing_balance, currency, created_at, updated_at)
			 VALUES ($1, $2, $3, $4, $5, 0, $6, $7, $6 - $7, 'KES', NOW(), NOW())
			 ON CONFLICT (tenant_id, account_id, period_year, period_month)
			 DO UPDATE SET
			   total_debits = account_balances.total_debits + EXCLUDED.total_debits,
			   total_credits = account_balances.total_credits + EXCLUDED.total_credits,
			   closing_balance = account_balances.opening_balance + (account_balances.total_debits + EXCLUDED.total_debits) - (account_balances.total_credits + EXCLUDED.total_credits),
			   updated_at = NOW()`,
			uuid.New(), entry.TenantID, line.AccountID, year, month,
			line.DebitAmount, line.CreditAmount)
		if err != nil {
			return fmt.Errorf("upsert account balance for account %s: %w", line.AccountID, err)
		}
	}

	return tx.Commit(ctx)
}

// EntryExistsBySourceEventAndID checks if a journal entry already exists for idempotency.
func (r *Repository) EntryExistsBySourceEventAndID(ctx context.Context, sourceEvent, sourceID string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM journal_entries WHERE source_event = $1 AND source_id = $2)`,
		sourceEvent, sourceID).Scan(&exists)
	return exists, err
}

// FindEntryByIDAndTenant returns a journal entry with its lines.
func (r *Repository) FindEntryByIDAndTenant(ctx context.Context, id uuid.UUID, tenantID string) (*model.JournalEntry, error) {
	entry := &model.JournalEntry{}
	err := r.pool.QueryRow(ctx,
		`SELECT id, tenant_id, reference, description, entry_date, status, source_event, source_id, total_debit, total_credit, posted_by, created_at, updated_at
		 FROM journal_entries WHERE id = $1 AND tenant_id = $2`, id, tenantID).
		Scan(&entry.ID, &entry.TenantID, &entry.Reference, &entry.Description, &entry.EntryDate,
			&entry.Status, &entry.SourceEvent, &entry.SourceID,
			&entry.TotalDebit, &entry.TotalCredit, &entry.PostedBy, &entry.CreatedAt, &entry.UpdatedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}

	lines, err := r.findLinesByEntryID(ctx, entry.ID)
	if err != nil {
		return nil, err
	}
	entry.Lines = lines
	return entry, nil
}

// ListEntries returns paginated journal entries for a tenant.
func (r *Repository) ListEntries(ctx context.Context, tenantID string, from, to *time.Time, page, size int) ([]model.JournalEntry, int64, error) {
	var totalCount int64
	var countArgs []any
	countQuery := `SELECT COUNT(*) FROM journal_entries WHERE tenant_id = $1`
	countArgs = append(countArgs, tenantID)

	if from != nil && to != nil {
		countQuery += ` AND entry_date BETWEEN $2 AND $3`
		countArgs = append(countArgs, *from, *to)
	}

	if err := r.pool.QueryRow(ctx, countQuery, countArgs...).Scan(&totalCount); err != nil {
		return nil, 0, err
	}

	listQuery := `SELECT id, tenant_id, reference, description, entry_date, status, source_event, source_id, total_debit, total_credit, posted_by, created_at, updated_at
		FROM journal_entries WHERE tenant_id = $1`
	listArgs := []any{tenantID}

	if from != nil && to != nil {
		listQuery += ` AND entry_date BETWEEN $2 AND $3`
		listArgs = append(listArgs, *from, *to)
	}

	offset := page * size
	argIdx := len(listArgs) + 1
	listQuery += fmt.Sprintf(` ORDER BY entry_date DESC LIMIT $%d OFFSET $%d`, argIdx, argIdx+1)
	listArgs = append(listArgs, size, offset)

	rows, err := r.pool.Query(ctx, listQuery, listArgs...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var entries []model.JournalEntry
	for rows.Next() {
		var e model.JournalEntry
		if err := rows.Scan(&e.ID, &e.TenantID, &e.Reference, &e.Description, &e.EntryDate,
			&e.Status, &e.SourceEvent, &e.SourceID,
			&e.TotalDebit, &e.TotalCredit, &e.PostedBy, &e.CreatedAt, &e.UpdatedAt); err != nil {
			return nil, 0, err
		}
		lines, err := r.findLinesByEntryID(ctx, e.ID)
		if err != nil {
			return nil, 0, err
		}
		e.Lines = lines
		entries = append(entries, e)
	}
	return entries, totalCount, nil
}

// --- Account Balances ---

// GetNetBalance returns the net balance (sum debits - sum credits) for an account.
func (r *Repository) GetNetBalance(ctx context.Context, accountID uuid.UUID, tenantID string) (decimal.Decimal, error) {
	var net decimal.Decimal
	err := r.pool.QueryRow(ctx,
		`SELECT COALESCE(SUM(debit_amount), 0) - COALESCE(SUM(credit_amount), 0)
		 FROM journal_lines jl
		 JOIN journal_entries je ON jl.entry_id = je.id
		 WHERE jl.account_id = $1 AND je.tenant_id = $2`,
		accountID, tenantID).Scan(&net)
	return net, err
}

// FindLedgerLines returns all journal lines for a given account.
func (r *Repository) FindLedgerLines(ctx context.Context, accountID uuid.UUID) ([]model.JournalLine, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, entry_id, tenant_id, account_id, line_no, description, debit_amount, credit_amount, currency
		 FROM journal_lines WHERE account_id = $1 ORDER BY line_no`, accountID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanLines(rows)
}

// --- internal helpers ---

func (r *Repository) findLinesByEntryID(ctx context.Context, entryID uuid.UUID) ([]model.JournalLine, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, entry_id, tenant_id, account_id, line_no, description, debit_amount, credit_amount, currency
		 FROM journal_lines WHERE entry_id = $1 ORDER BY line_no`, entryID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanLines(rows)
}

func scanLines(rows pgx.Rows) ([]model.JournalLine, error) {
	var lines []model.JournalLine
	for rows.Next() {
		var l model.JournalLine
		if err := rows.Scan(&l.ID, &l.EntryID, &l.TenantID, &l.AccountID, &l.LineNo,
			&l.Description, &l.DebitAmount, &l.CreditAmount, &l.Currency); err != nil {
			return nil, err
		}
		lines = append(lines, l)
	}
	return lines, rows.Err()
}

func (r *Repository) scanAccount(ctx context.Context, query string, args ...any) (*model.ChartOfAccount, error) {
	a := &model.ChartOfAccount{}
	err := r.pool.QueryRow(ctx, query, args...).
		Scan(&a.ID, &a.TenantID, &a.Code, &a.Name, &a.AccountType, &a.BalanceType,
			&a.ParentID, &a.Description, &a.IsActive, &a.CreatedAt, &a.UpdatedAt)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	return a, nil
}

func (r *Repository) scanAccounts(ctx context.Context, query string, args ...any) ([]model.ChartOfAccount, error) {
	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accounts []model.ChartOfAccount
	for rows.Next() {
		var a model.ChartOfAccount
		if err := rows.Scan(&a.ID, &a.TenantID, &a.Code, &a.Name, &a.AccountType, &a.BalanceType,
			&a.ParentID, &a.Description, &a.IsActive, &a.CreatedAt, &a.UpdatedAt); err != nil {
			return nil, err
		}
		accounts = append(accounts, a)
	}
	return accounts, rows.Err()
}
