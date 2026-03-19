package service

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/account/event"
	"github.com/athena-lms/go-services/internal/account/model"
	"github.com/athena-lms/go-services/internal/account/repository"
	"github.com/athena-lms/go-services/internal/common/errors"
)

var (
	daysInYear = decimal.NewFromInt(365)
	whtRate    = decimal.NewFromFloat(0.15) // 15% KRA withholding tax
)

// InterestService handles interest accrual and posting.
type InterestService struct {
	repo      *repository.Repository
	publisher *event.Publisher
	logger    *zap.Logger
}

// NewInterestService creates a new InterestService.
func NewInterestService(repo *repository.Repository, publisher *event.Publisher, logger *zap.Logger) *InterestService {
	return &InterestService{repo: repo, publisher: publisher, logger: logger}
}

// AccrueInterestForDate calculates and records daily interest for all eligible accounts.
func (s *InterestService) AccrueInterestForDate(ctx context.Context, tenantID string, date time.Time) (int, error) {
	accounts, err := s.repo.ListAccountsEligibleForInterest(ctx, tenantID)
	if err != nil {
		return 0, fmt.Errorf("list accounts for interest: %w", err)
	}

	accrued := 0
	for _, account := range accounts {
		if err := s.accrueForAccount(ctx, account, date); err != nil {
			s.logger.Warn("Failed to accrue interest for account",
				zap.String("accountId", account.ID.String()),
				zap.Error(err))
			continue
		}
		accrued++
	}

	s.logger.Info("Interest accrual completed",
		zap.String("date", date.Format("2006-01-02")),
		zap.Int("accountsAccrued", accrued),
		zap.Int("totalEligible", len(accounts)))

	return accrued, nil
}

func (s *InterestService) accrueForAccount(ctx context.Context, account *model.Account, date time.Time) error {
	bal, err := s.repo.GetBalanceByAccountID(ctx, account.ID)
	if err != nil {
		return err
	}

	// Determine rate (override takes precedence)
	rate := decimal.Zero
	if account.InterestRateOverride != nil {
		rate = *account.InterestRateOverride
	}
	// If no override, rate should come from the deposit product (passed via account or fetched)
	// For now we use whatever is on the account or override

	if rate.IsZero() {
		return nil // no interest to accrue
	}

	balance := bal.AvailableBalance
	if balance.LessThanOrEqual(decimal.Zero) {
		return nil
	}

	// Daily interest = balance * (rate/100) / 365
	dailyAmount := balance.Mul(rate).Div(decimal.NewFromInt(100)).Div(daysInYear).Round(4)
	if dailyAmount.IsZero() {
		return nil
	}

	tx, err := s.repo.Pool().Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	accrual := &model.InterestAccrual{
		TenantID:    account.TenantID,
		AccountID:   account.ID,
		AccrualDate: date,
		BalanceUsed: balance,
		Rate:        rate,
		DailyAmount: dailyAmount,
		Posted:      false,
	}
	if err := s.repo.CreateInterestAccrual(ctx, tx, accrual); err != nil {
		return err
	}

	// Update account's accrued interest total
	currentAccrued := decimal.Zero
	if account.AccruedInterest != nil {
		currentAccrued = *account.AccruedInterest
	}
	newAccrued := currentAccrued.Add(dailyAmount)
	if err := s.repo.UpdateAccountAccruedInterest(ctx, tx, account.ID, newAccrued, date); err != nil {
		return err
	}

	return tx.Commit(ctx)
}

// PostAccruedInterest posts all unposted interest for an account, deducting 15% WHT.
func (s *InterestService) PostAccruedInterest(ctx context.Context, accountID uuid.UUID, tenantID, postedBy string) (*model.InterestPosting, error) {
	account, err := s.repo.GetAccountByIDAndTenant(ctx, accountID, tenantID)
	if err != nil {
		return nil, errors.NotFoundResource("Account", accountID)
	}

	unpostedTotal, err := s.repo.SumUnpostedInterest(ctx, accountID)
	if err != nil {
		return nil, err
	}
	if unpostedTotal.IsZero() {
		return nil, errors.NewBusinessError("No unposted interest to post")
	}

	// Calculate WHT (15% for Kenya)
	wht := unpostedTotal.Mul(whtRate).Round(2)
	netInterest := unpostedTotal.Sub(wht).Round(2)

	tx, err := s.repo.Pool().Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback(ctx)

	// Credit net interest to account
	balance, err := s.repo.GetBalanceForUpdate(ctx, tx, accountID)
	if err != nil {
		return nil, err
	}

	balance.AvailableBalance = balance.AvailableBalance.Add(netInterest)
	balance.CurrentBalance = balance.CurrentBalance.Add(netInterest)
	balance.LedgerBalance = balance.LedgerBalance.Add(netInterest)
	if err := s.repo.UpdateBalance(ctx, tx, balance); err != nil {
		return nil, err
	}

	// Create transaction record
	newBal := balance.AvailableBalance
	desc := "Interest posting (net of 15% WHT)"
	txn := &model.AccountTransaction{
		TenantID:        tenantID,
		AccountID:       accountID,
		TransactionType: model.TransactionTypeCredit,
		Amount:          netInterest,
		BalanceAfter:    &newBal,
		Description:     &desc,
		Channel:         "SYSTEM",
	}
	if err := s.repo.CreateTransaction(ctx, tx, txn); err != nil {
		return nil, err
	}

	// Find earliest unposted accrual date for period_start
	accruals, err := s.repo.GetUnpostedAccruals(ctx, accountID)
	if err != nil {
		return nil, err
	}
	periodStart := time.Now()
	if len(accruals) > 0 {
		periodStart = accruals[0].AccrualDate
	}

	posting := &model.InterestPosting{
		TenantID:       tenantID,
		AccountID:      accountID,
		PeriodStart:    periodStart,
		PeriodEnd:      time.Now(),
		GrossInterest:  unpostedTotal,
		WithholdingTax: wht,
		NetInterest:    netInterest,
		TransactionID:  &txn.ID,
		PostedBy:       &postedBy,
	}
	if err := s.repo.CreateInterestPosting(ctx, tx, posting); err != nil {
		return nil, err
	}

	// Mark all accruals as posted
	if err := s.repo.MarkAccrualsPosted(ctx, tx, accountID, posting.ID); err != nil {
		return nil, err
	}

	// Update account interest state
	if err := s.repo.UpdateAccountInterestPosted(ctx, tx, accountID, time.Now()); err != nil {
		return nil, err
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}

	s.logger.Info("Interest posted",
		zap.String("accountId", accountID.String()),
		zap.String("gross", unpostedTotal.String()),
		zap.String("wht", wht.String()),
		zap.String("net", netInterest.String()))

	_ = account // used for logging context
	return posting, nil
}

// GetInterestSummary returns accrual and posting history for an account.
func (s *InterestService) GetInterestSummary(ctx context.Context, accountID uuid.UUID, tenantID string) (*InterestSummaryResponse, error) {
	_, err := s.repo.GetAccountByIDAndTenant(ctx, accountID, tenantID)
	if err != nil {
		return nil, errors.NotFoundResource("Account", accountID)
	}

	accruals, err := s.repo.ListInterestAccruals(ctx, accountID, 90)
	if err != nil {
		return nil, err
	}

	postings, err := s.repo.ListInterestPostings(ctx, accountID)
	if err != nil {
		return nil, err
	}

	unposted, err := s.repo.SumUnpostedInterest(ctx, accountID)
	if err != nil {
		return nil, err
	}

	return &InterestSummaryResponse{
		AccountID:       accountID,
		UnpostedTotal:   unposted,
		RecentAccruals:  accruals,
		PostingHistory:  postings,
	}, nil
}

// InterestSummaryResponse holds interest summary data.
type InterestSummaryResponse struct {
	AccountID      uuid.UUID                `json:"accountId"`
	UnpostedTotal  decimal.Decimal          `json:"unpostedTotal"`
	RecentAccruals []*model.InterestAccrual `json:"recentAccruals"`
	PostingHistory []*model.InterestPosting `json:"postingHistory"`
}
