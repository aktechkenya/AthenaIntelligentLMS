package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/shopspring/decimal"

	"github.com/athena-lms/go-services/internal/account/model"
)

// ─── Interest Accrual ────────────────────────────────────────────────────────

// CreateInterestAccrual inserts a daily accrual record.
func (r *Repository) CreateInterestAccrual(ctx context.Context, tx pgx.Tx, a *model.InterestAccrual) error {
	a.ID = uuid.New()
	a.CreatedAt = time.Now()
	_, err := tx.Exec(ctx,
		`INSERT INTO interest_accruals (id, tenant_id, account_id, accrual_date, balance_used, rate, daily_amount, posted, created_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
		a.ID, a.TenantID, a.AccountID, a.AccrualDate, a.BalanceUsed.String(), a.Rate.String(), a.DailyAmount.String(), a.Posted, a.CreatedAt,
	)
	return err
}

// GetUnpostedAccruals returns all unposted accrual records for an account.
func (r *Repository) GetUnpostedAccruals(ctx context.Context, accountID uuid.UUID) ([]*model.InterestAccrual, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, tenant_id, account_id, accrual_date, balance_used, rate, daily_amount, posted, posting_id, created_at
		FROM interest_accruals
		WHERE account_id = $1 AND posted = false
		ORDER BY accrual_date ASC`,
		accountID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accruals []*model.InterestAccrual
	for rows.Next() {
		a := &model.InterestAccrual{}
		if err := rows.Scan(&a.ID, &a.TenantID, &a.AccountID, &a.AccrualDate,
			&a.BalanceUsed, &a.Rate, &a.DailyAmount, &a.Posted, &a.PostingID, &a.CreatedAt); err != nil {
			return nil, err
		}
		accruals = append(accruals, a)
	}
	return accruals, nil
}

// MarkAccrualsPosted marks accrual records as posted.
func (r *Repository) MarkAccrualsPosted(ctx context.Context, tx pgx.Tx, accountID uuid.UUID, postingID uuid.UUID) error {
	_, err := tx.Exec(ctx,
		`UPDATE interest_accruals SET posted = true, posting_id = $1
		WHERE account_id = $2 AND posted = false`,
		postingID, accountID)
	return err
}

// SumUnpostedInterest returns the total unposted interest for an account.
func (r *Repository) SumUnpostedInterest(ctx context.Context, accountID uuid.UUID) (decimal.Decimal, error) {
	var total decimal.Decimal
	err := r.pool.QueryRow(ctx,
		`SELECT COALESCE(SUM(daily_amount), 0)
		FROM interest_accruals
		WHERE account_id = $1 AND posted = false`,
		accountID).Scan(&total)
	return total, err
}

// ─── Interest Posting ────────────────────────────────────────────────────────

// CreateInterestPosting inserts an interest posting record.
func (r *Repository) CreateInterestPosting(ctx context.Context, tx pgx.Tx, p *model.InterestPosting) error {
	p.ID = uuid.New()
	p.PostedAt = time.Now()
	_, err := tx.Exec(ctx,
		`INSERT INTO interest_postings (id, tenant_id, account_id, period_start, period_end,
			gross_interest, withholding_tax, net_interest, transaction_id, posted_at, posted_by)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
		p.ID, p.TenantID, p.AccountID, p.PeriodStart, p.PeriodEnd,
		p.GrossInterest.String(), p.WithholdingTax.String(), p.NetInterest.String(),
		p.TransactionID, p.PostedAt, p.PostedBy,
	)
	return err
}

// ListInterestPostings returns interest postings for an account.
func (r *Repository) ListInterestPostings(ctx context.Context, accountID uuid.UUID) ([]*model.InterestPosting, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, tenant_id, account_id, period_start, period_end,
			gross_interest, withholding_tax, net_interest, transaction_id, posted_at, posted_by
		FROM interest_postings
		WHERE account_id = $1
		ORDER BY posted_at DESC`,
		accountID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var postings []*model.InterestPosting
	for rows.Next() {
		p := &model.InterestPosting{}
		if err := rows.Scan(&p.ID, &p.TenantID, &p.AccountID, &p.PeriodStart, &p.PeriodEnd,
			&p.GrossInterest, &p.WithholdingTax, &p.NetInterest, &p.TransactionID,
			&p.PostedAt, &p.PostedBy); err != nil {
			return nil, err
		}
		postings = append(postings, p)
	}
	return postings, nil
}

// ListInterestAccruals returns accrual records for an account.
func (r *Repository) ListInterestAccruals(ctx context.Context, accountID uuid.UUID, limit int) ([]*model.InterestAccrual, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, tenant_id, account_id, accrual_date, balance_used, rate, daily_amount, posted, posting_id, created_at
		FROM interest_accruals
		WHERE account_id = $1
		ORDER BY accrual_date DESC
		LIMIT $2`,
		accountID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accruals []*model.InterestAccrual
	for rows.Next() {
		a := &model.InterestAccrual{}
		if err := rows.Scan(&a.ID, &a.TenantID, &a.AccountID, &a.AccrualDate,
			&a.BalanceUsed, &a.Rate, &a.DailyAmount, &a.Posted, &a.PostingID, &a.CreatedAt); err != nil {
			return nil, err
		}
		accruals = append(accruals, a)
	}
	return accruals, nil
}

// ─── Account Updates for Interest/Dormancy ───────────────────────────────────

// UpdateAccountAccruedInterest updates accrued interest and last accrual date.
func (r *Repository) UpdateAccountAccruedInterest(ctx context.Context, tx pgx.Tx, accountID uuid.UUID, accrued decimal.Decimal, accrualDate time.Time) error {
	_, err := tx.Exec(ctx,
		`UPDATE accounts SET accrued_interest = $1, last_interest_accrual_date = $2, updated_at = NOW()
		WHERE id = $3`,
		accrued.String(), accrualDate, accountID)
	return err
}

// UpdateAccountInterestPosted updates the account after interest posting.
func (r *Repository) UpdateAccountInterestPosted(ctx context.Context, tx pgx.Tx, accountID uuid.UUID, postingDate time.Time) error {
	_, err := tx.Exec(ctx,
		`UPDATE accounts SET accrued_interest = 0, last_interest_posting_date = $1, updated_at = NOW()
		WHERE id = $2`,
		postingDate, accountID)
	return err
}

// UpdateAccountLastTransactionDate updates the last transaction date.
func (r *Repository) UpdateAccountLastTransactionDate(ctx context.Context, tx pgx.Tx, accountID uuid.UUID) error {
	_, err := tx.Exec(ctx,
		`UPDATE accounts SET last_transaction_date = NOW(), updated_at = NOW()
		WHERE id = $1`, accountID)
	return err
}

// SetAccountDormant marks an account as dormant.
func (r *Repository) SetAccountDormant(ctx context.Context, accountID uuid.UUID) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE accounts SET status = 'DORMANT', dormant_since = NOW(), updated_at = NOW()
		WHERE id = $1`, accountID)
	return err
}

// ReactivateAccount clears dormancy and sets status to ACTIVE.
func (r *Repository) ReactivateAccount(ctx context.Context, tx pgx.Tx, accountID uuid.UUID) error {
	_, err := tx.Exec(ctx,
		`UPDATE accounts SET status = 'ACTIVE', dormant_since = NULL, updated_at = NOW()
		WHERE id = $1`, accountID)
	return err
}

// CloseAccount sets account to CLOSED with reason and timestamp.
func (r *Repository) CloseAccount(ctx context.Context, accountID uuid.UUID, reason string) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE accounts SET status = 'CLOSED', closed_at = NOW(), closure_reason = $1, updated_at = NOW()
		WHERE id = $2`, reason, accountID)
	return err
}

// ListAccountsEligibleForInterest returns active accounts with a deposit product.
func (r *Repository) ListAccountsEligibleForInterest(ctx context.Context, tenantID string) ([]*model.Account, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT `+accountCols+` FROM accounts
		WHERE tenant_id = $1 AND status = 'ACTIVE' AND deposit_product_id IS NOT NULL
		ORDER BY id`,
		tenantID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accounts []*model.Account
	for rows.Next() {
		a := &model.Account{}
		if err := rows.Scan(
			&a.ID, &a.TenantID, &a.AccountNumber, &a.CustomerID,
			&a.AccountType, &a.Status, &a.Currency, &a.KycTier,
			&a.DailyTransactionLimit, &a.MonthlyTransactionLimit,
			&a.AccountName,
			&a.DepositProductID, &a.BranchID, &a.OpenedBy,
			&a.ClosedAt, &a.ClosureReason, &a.LastTransactionDate, &a.DormantSince,
			&a.MaturityDate, &a.TermDays, &a.LockedAmount, &a.AutoRenew,
			&a.AccruedInterest, &a.LastInterestAccrualDate, &a.LastInterestPostingDate,
			&a.InterestRateOverride,
			&a.CreatedAt, &a.UpdatedAt,
		); err != nil {
			return nil, err
		}
		accounts = append(accounts, a)
	}
	return accounts, nil
}

// ListDormancyCandidates returns active accounts with last transaction older than threshold days.
func (r *Repository) ListDormancyCandidates(ctx context.Context, tenantID string, thresholdDays int) ([]*model.Account, error) {
	rows, err := r.pool.Query(ctx,
		fmt.Sprintf(`SELECT %s FROM accounts
		WHERE tenant_id = $1 AND status = 'ACTIVE'
		  AND last_transaction_date IS NOT NULL
		  AND last_transaction_date < NOW() - INTERVAL '%d days'
		ORDER BY last_transaction_date ASC`, accountCols, thresholdDays),
		tenantID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accounts []*model.Account
	for rows.Next() {
		a := &model.Account{}
		if err := rows.Scan(
			&a.ID, &a.TenantID, &a.AccountNumber, &a.CustomerID,
			&a.AccountType, &a.Status, &a.Currency, &a.KycTier,
			&a.DailyTransactionLimit, &a.MonthlyTransactionLimit,
			&a.AccountName,
			&a.DepositProductID, &a.BranchID, &a.OpenedBy,
			&a.ClosedAt, &a.ClosureReason, &a.LastTransactionDate, &a.DormantSince,
			&a.MaturityDate, &a.TermDays, &a.LockedAmount, &a.AutoRenew,
			&a.AccruedInterest, &a.LastInterestAccrualDate, &a.LastInterestPostingDate,
			&a.InterestRateOverride,
			&a.CreatedAt, &a.UpdatedAt,
		); err != nil {
			return nil, err
		}
		accounts = append(accounts, a)
	}
	return accounts, nil
}

// ListMaturedFixedDeposits returns FD accounts that have matured.
func (r *Repository) ListMaturedFixedDeposits(ctx context.Context, tenantID string, asOfDate time.Time) ([]*model.Account, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT `+accountCols+` FROM accounts
		WHERE tenant_id = $1 AND account_type = 'FIXED_DEPOSIT' AND status = 'ACTIVE'
		  AND maturity_date IS NOT NULL AND maturity_date <= $2
		ORDER BY maturity_date ASC`,
		tenantID, asOfDate)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accounts []*model.Account
	for rows.Next() {
		a := &model.Account{}
		if err := rows.Scan(
			&a.ID, &a.TenantID, &a.AccountNumber, &a.CustomerID,
			&a.AccountType, &a.Status, &a.Currency, &a.KycTier,
			&a.DailyTransactionLimit, &a.MonthlyTransactionLimit,
			&a.AccountName,
			&a.DepositProductID, &a.BranchID, &a.OpenedBy,
			&a.ClosedAt, &a.ClosureReason, &a.LastTransactionDate, &a.DormantSince,
			&a.MaturityDate, &a.TermDays, &a.LockedAmount, &a.AutoRenew,
			&a.AccruedInterest, &a.LastInterestAccrualDate, &a.LastInterestPostingDate,
			&a.InterestRateOverride,
			&a.CreatedAt, &a.UpdatedAt,
		); err != nil {
			return nil, err
		}
		accounts = append(accounts, a)
	}
	return accounts, nil
}

// UpdateAccountForFDMaturity updates an FD account after maturity processing.
func (r *Repository) UpdateAccountForFDMaturity(ctx context.Context, accountID uuid.UUID, status model.AccountStatus, newMaturity *time.Time) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE accounts SET status = $1, maturity_date = $2, updated_at = NOW()
		WHERE id = $3`,
		status, newMaturity, accountID)
	return err
}
