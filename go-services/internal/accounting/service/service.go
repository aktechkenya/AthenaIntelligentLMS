package service

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/accounting/event"
	"github.com/athena-lms/go-services/internal/accounting/model"
	"github.com/athena-lms/go-services/internal/accounting/repository"
	"github.com/athena-lms/go-services/internal/common/errors"
)

// AccountingService implements the accounting domain logic.
type AccountingService struct {
	repo      *repository.Repository
	publisher *event.Publisher
	logger    *zap.Logger
}

// New creates a new AccountingService.
func New(repo *repository.Repository, publisher *event.Publisher, logger *zap.Logger) *AccountingService {
	return &AccountingService{repo: repo, publisher: publisher, logger: logger}
}

// --- Chart of Accounts ---

// CreateAccount creates a new GL account.
func (s *AccountingService) CreateAccount(ctx context.Context, req model.CreateAccountRequest, tenantID string) (*model.AccountResponse, error) {
	existing, err := s.repo.FindAccountByTenantAndCode(ctx, tenantID, req.Code)
	if err != nil {
		return nil, fmt.Errorf("check existing account: %w", err)
	}
	if existing != nil {
		return nil, errors.NewBusinessError("Account code already exists: " + req.Code)
	}

	account := &model.ChartOfAccount{
		TenantID:    tenantID,
		Code:        req.Code,
		Name:        req.Name,
		AccountType: req.AccountType,
		BalanceType: req.BalanceType,
		ParentID:    req.ParentID,
		Description: req.Description,
		IsActive:    true,
	}

	if err := s.repo.CreateAccount(ctx, account); err != nil {
		return nil, fmt.Errorf("create account: %w", err)
	}

	resp := model.ToAccountResponse(account)
	return &resp, nil
}

// ListAccounts lists GL accounts for a tenant (with optional type filter), falling back to system accounts.
func (s *AccountingService) ListAccounts(ctx context.Context, tenantID string, accountType *model.AccountType) ([]model.AccountResponse, error) {
	var accounts []model.ChartOfAccount
	var err error

	if accountType != nil {
		accounts, err = s.repo.ListActiveAccountsByType(ctx, tenantID, *accountType)
	} else {
		accounts, err = s.repo.ListActiveAccounts(ctx, tenantID)
	}
	if err != nil {
		return nil, err
	}

	// Fall back to system accounts if tenant has none
	if len(accounts) == 0 {
		if accountType != nil {
			accounts, err = s.repo.ListActiveAccountsByType(ctx, "system", *accountType)
		} else {
			accounts, err = s.repo.ListActiveAccounts(ctx, "system")
		}
		if err != nil {
			return nil, err
		}
	}

	result := make([]model.AccountResponse, 0, len(accounts))
	for i := range accounts {
		result = append(result, model.ToAccountResponse(&accounts[i]))
	}
	return result, nil
}

// GetAccount returns a single GL account by ID.
func (s *AccountingService) GetAccount(ctx context.Context, id uuid.UUID, tenantID string) (*model.AccountResponse, error) {
	account, err := s.repo.FindAccountByIDAndTenantIn(ctx, id, []string{tenantID, "system"})
	if err != nil {
		return nil, err
	}
	if account == nil {
		return nil, errors.NotFoundResource("Account", id)
	}
	resp := model.ToAccountResponse(account)
	return &resp, nil
}

// GetAccountByCode returns a GL account by code.
func (s *AccountingService) GetAccountByCode(ctx context.Context, code, tenantID string) (*model.AccountResponse, error) {
	account, err := s.repo.FindAccountByCodeAndTenantIn(ctx, code, []string{tenantID, "system"})
	if err != nil {
		return nil, err
	}
	if account == nil {
		return nil, errors.NotFoundResource("Account", code)
	}
	resp := model.ToAccountResponse(account)
	return &resp, nil
}

// --- Journal Entries ---

// PostEntry validates and posts a manual journal entry.
func (s *AccountingService) PostEntry(ctx context.Context, req model.PostJournalEntryRequest, tenantID, userID string) (*model.JournalEntryResponse, error) {
	if len(req.Lines) < 2 {
		return nil, errors.NewBusinessError("Journal entry must have at least 2 lines")
	}

	totalDebit := decimal.Zero
	totalCredit := decimal.Zero
	for _, line := range req.Lines {
		totalDebit = totalDebit.Add(line.DebitAmount)
		totalCredit = totalCredit.Add(line.CreditAmount)
	}
	if !totalDebit.Equal(totalCredit) {
		return nil, errors.NewBusinessError(fmt.Sprintf(
			"Journal entry is not balanced: debits=%s credits=%s", totalDebit, totalCredit))
	}

	entryDate := time.Now()
	if req.EntryDate != nil {
		entryDate = *req.EntryDate
	}

	entry := &model.JournalEntry{
		TenantID:    tenantID,
		Reference:   req.Reference,
		Description: req.Description,
		EntryDate:   entryDate,
		Status:      model.EntryStatusPosted,
		TotalDebit:  totalDebit,
		TotalCredit: totalCredit,
		PostedBy:    &userID,
	}

	for i, lr := range req.Lines {
		currency := lr.Currency
		if currency == "" {
			currency = "KES"
		}
		entry.Lines = append(entry.Lines, model.JournalLine{
			AccountID:    lr.AccountID,
			LineNo:       i + 1,
			Description:  lr.Description,
			DebitAmount:  lr.DebitAmount,
			CreditAmount: lr.CreditAmount,
			Currency:     currency,
		})
	}

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return nil, fmt.Errorf("post journal entry: %w", err)
	}

	s.publisher.PublishJournalPosted(ctx, entry)

	resp := model.ToJournalEntryResponse(entry)
	return &resp, nil
}

// ListEntries returns paginated journal entries.
func (s *AccountingService) ListEntries(ctx context.Context, tenantID string, from, to *time.Time, page, size int) ([]model.JournalEntryResponse, int64, error) {
	entries, total, err := s.repo.ListEntries(ctx, tenantID, from, to, page, size)
	if err != nil {
		return nil, 0, err
	}

	result := make([]model.JournalEntryResponse, 0, len(entries))
	for i := range entries {
		result = append(result, model.ToJournalEntryResponse(&entries[i]))
	}
	return result, total, nil
}

// GetEntry returns a single journal entry by ID.
func (s *AccountingService) GetEntry(ctx context.Context, id uuid.UUID, tenantID string) (*model.JournalEntryResponse, error) {
	entry, err := s.repo.FindEntryByIDAndTenant(ctx, id, tenantID)
	if err != nil {
		return nil, err
	}
	if entry == nil {
		return nil, errors.NotFoundResource("JournalEntry", id)
	}
	resp := model.ToJournalEntryResponse(entry)
	return &resp, nil
}

// --- Balances & Reporting ---

// GetBalance returns the balance for a GL account.
func (s *AccountingService) GetBalance(ctx context.Context, accountID uuid.UUID, tenantID string, year, month int) (*model.BalanceResponse, error) {
	account, err := s.repo.FindAccountByIDAndTenantIn(ctx, accountID, []string{tenantID, "system"})
	if err != nil {
		return nil, err
	}
	if account == nil {
		return nil, errors.NotFoundResource("Account", accountID)
	}

	net, err := s.repo.GetNetBalance(ctx, accountID, tenantID)
	if err != nil {
		return nil, err
	}

	// For CREDIT-normal accounts, flip sign for display
	if account.BalanceType == model.BalanceTypeCredit {
		net = net.Neg()
	}

	return &model.BalanceResponse{
		AccountID:   accountID,
		AccountCode: account.Code,
		AccountName: account.Name,
		AccountType: string(account.AccountType),
		BalanceType: string(account.BalanceType),
		Balance:     net,
		Currency:    "KES",
		PeriodYear:  year,
		PeriodMonth: month,
	}, nil
}

// GetLedger returns all journal lines for a GL account.
func (s *AccountingService) GetLedger(ctx context.Context, accountID uuid.UUID, tenantID string) ([]model.JournalLineResponse, error) {
	account, err := s.repo.FindAccountByIDAndTenantIn(ctx, accountID, []string{tenantID, "system"})
	if err != nil {
		return nil, err
	}
	if account == nil {
		return nil, errors.NotFoundResource("Account", accountID)
	}

	lines, err := s.repo.FindLedgerLines(ctx, accountID)
	if err != nil {
		return nil, err
	}

	result := make([]model.JournalLineResponse, 0, len(lines))
	for i := range lines {
		result = append(result, model.ToJournalLineResponse(&lines[i]))
	}
	return result, nil
}

// GetTrialBalance returns the trial balance for a tenant.
func (s *AccountingService) GetTrialBalance(ctx context.Context, tenantID string, year, month int) (*model.TrialBalanceResponse, error) {
	accounts, err := s.repo.ListActiveAccounts(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	if len(accounts) == 0 {
		accounts, err = s.repo.ListActiveAccounts(ctx, "system")
		if err != nil {
			return nil, err
		}
	}

	rows := make([]model.BalanceResponse, 0, len(accounts))
	totalDr := decimal.Zero
	totalCr := decimal.Zero

	for _, acc := range accounts {
		net, err := s.repo.GetNetBalance(ctx, acc.ID, tenantID)
		if err != nil {
			return nil, err
		}

		row := model.BalanceResponse{
			AccountID:   acc.ID,
			AccountCode: acc.Code,
			AccountName: acc.Name,
			AccountType: string(acc.AccountType),
			BalanceType: string(acc.BalanceType),
			Balance:     net.Abs(),
			Currency:    "KES",
			PeriodYear:  year,
			PeriodMonth: month,
		}
		rows = append(rows, row)

		if net.GreaterThanOrEqual(decimal.Zero) {
			totalDr = totalDr.Add(net.Abs())
		} else {
			totalCr = totalCr.Add(net.Abs())
		}
	}

	return &model.TrialBalanceResponse{
		PeriodYear:   year,
		PeriodMonth:  month,
		Accounts:     rows,
		TotalDebits:  totalDr,
		TotalCredits: totalCr,
		Balanced:     totalDr.Equal(totalCr),
	}, nil
}

// --- Event-driven journal posting ---

// EntryExists checks if a journal entry already exists for idempotency.
func (s *AccountingService) EntryExists(ctx context.Context, sourceEvent, sourceID string) bool {
	if sourceID == "" {
		return false
	}
	exists, err := s.repo.EntryExistsBySourceEventAndID(ctx, sourceEvent, sourceID)
	if err != nil {
		s.logger.Error("Failed to check entry existence", zap.Error(err))
		return false
	}
	return exists
}

// PostLoanDisbursement creates a journal entry for a loan disbursement.
// DR Loans Receivable (1100) / CR Cash (1000)
func (s *AccountingService) PostLoanDisbursement(ctx context.Context, tenantID, applicationID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1100")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "DISB-"+applicationID,
		"Loan disbursement for application "+applicationID,
		"loan.disbursed", applicationID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted disbursement journal", zap.String("applicationId", applicationID), zap.String("amount", amount.String()))
	return nil
}

// PostRepayment creates a journal entry for a loan repayment with breakdown.
func (s *AccountingService) PostRepayment(ctx context.Context, tenantID, paymentID string, amount decimal.Decimal, payload map[string]any) error {
	principal := getDecimalFromPayload(payload, "principalApplied")
	interest := getDecimalFromPayload(payload, "interestApplied")
	fees := getDecimalFromPayload(payload, "feeApplied")
	penalties := getDecimalFromPayload(payload, "penaltyApplied")

	// Fallback: if breakdown doesn't sum to amount, treat full amount as principal
	breakdownTotal := principal.Add(interest).Add(fees).Add(penalties)
	if breakdownTotal.IsZero() || !breakdownTotal.Equal(amount) {
		principal = amount
		interest = decimal.Zero
		fees = decimal.Zero
		penalties = decimal.Zero
	}

	cashAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}
	loansAccount, err := s.resolveAccountID(ctx, tenantID, "1100")
	if err != nil {
		return err
	}

	sourceEvent := "payment.completed"
	description := "Loan repayment payment " + paymentID
	postedBy := "system"

	entry := &model.JournalEntry{
		TenantID:    tenantID,
		Reference:   "RPMT-" + paymentID,
		Description: &description,
		EntryDate:   time.Now(),
		Status:      model.EntryStatusPosted,
		SourceEvent: &sourceEvent,
		SourceID:    &paymentID,
		TotalDebit:  amount,
		TotalCredit: amount,
		PostedBy:    &postedBy,
	}

	lineNo := 1
	// Line 1: DR Cash - total received
	entry.Lines = append(entry.Lines, model.JournalLine{
		AccountID: cashAccount, LineNo: lineNo, DebitAmount: amount, CreditAmount: decimal.Zero, Currency: "KES",
	})
	lineNo++

	// Line 2: CR Loans Receivable - principal portion
	if principal.GreaterThan(decimal.Zero) {
		entry.Lines = append(entry.Lines, model.JournalLine{
			AccountID: loansAccount, LineNo: lineNo, DebitAmount: decimal.Zero, CreditAmount: principal, Currency: "KES",
		})
		lineNo++
	}

	// Line 3: CR Interest Income (4000)
	if interest.GreaterThan(decimal.Zero) {
		interestAccount, err := s.resolveAccountID(ctx, tenantID, "4000")
		if err != nil {
			return err
		}
		entry.Lines = append(entry.Lines, model.JournalLine{
			AccountID: interestAccount, LineNo: lineNo, DebitAmount: decimal.Zero, CreditAmount: interest, Currency: "KES",
		})
		lineNo++
	}

	// Line 4: CR Fee Income (4100)
	if fees.GreaterThan(decimal.Zero) {
		feeAccount, err := s.resolveAccountID(ctx, tenantID, "4100")
		if err != nil {
			return err
		}
		entry.Lines = append(entry.Lines, model.JournalLine{
			AccountID: feeAccount, LineNo: lineNo, DebitAmount: decimal.Zero, CreditAmount: fees, Currency: "KES",
		})
		lineNo++
	}

	// Line 5: CR Penalty Income (4200)
	if penalties.GreaterThan(decimal.Zero) {
		penaltyAccount, err := s.resolveAccountID(ctx, tenantID, "4200")
		if err != nil {
			return err
		}
		entry.Lines = append(entry.Lines, model.JournalLine{
			AccountID: penaltyAccount, LineNo: lineNo, DebitAmount: decimal.Zero, CreditAmount: penalties, Currency: "KES",
		})
	}

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted repayment journal",
		zap.String("paymentId", paymentID), zap.String("amount", amount.String()),
		zap.String("principal", principal.String()), zap.String("interest", interest.String()),
		zap.String("fees", fees.String()), zap.String("penalties", penalties.String()))
	return nil
}

// PostPaymentReversal creates a journal entry for a payment reversal.
// DR Loans Receivable (1100) / CR Cash (1000)
func (s *AccountingService) PostPaymentReversal(ctx context.Context, tenantID, paymentID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1100")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "REV-"+paymentID,
		"Payment reversal for "+paymentID,
		"payment.reversed", paymentID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	return nil
}

// PostOverdraftDrawn creates a journal entry for an overdraft draw.
// DR 1250 Overdraft Receivable / CR 1000 Cash
func (s *AccountingService) PostOverdraftDrawn(ctx context.Context, tenantID, sourceID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1250")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "OD-DRAW-"+sourceID,
		"Overdraft drawn "+sourceID, "overdraft.drawn", sourceID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted overdraft drawn journal", zap.String("sourceId", sourceID), zap.String("amount", amount.String()))
	return nil
}

// PostOverdraftRepaid creates a journal entry for an overdraft repayment.
// DR 1000 Cash / CR 1250 Overdraft Receivable
func (s *AccountingService) PostOverdraftRepaid(ctx context.Context, tenantID, sourceID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "1250")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "OD-RPMT-"+sourceID,
		"Overdraft repayment "+sourceID, "overdraft.repaid", sourceID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted overdraft repayment journal", zap.String("sourceId", sourceID), zap.String("amount", amount.String()))
	return nil
}

// PostOverdraftInterestCharged creates a journal entry for overdraft interest.
// DR 1250 Overdraft Receivable / CR 4300 Overdraft Interest Income
func (s *AccountingService) PostOverdraftInterestCharged(ctx context.Context, tenantID, sourceID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1250")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "4300")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "OD-INT-"+sourceID,
		"Overdraft interest charged "+sourceID, "overdraft.interest.charged", sourceID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted overdraft interest journal", zap.String("sourceId", sourceID), zap.String("amount", amount.String()))
	return nil
}

// PostOverdraftFeeCharged creates a journal entry for an overdraft fee.
// DR 1250 Overdraft Receivable / CR 4100 Fee Income
func (s *AccountingService) PostOverdraftFeeCharged(ctx context.Context, tenantID, sourceID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1250")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "4100")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "OD-FEE-"+sourceID,
		"Overdraft fee charged "+sourceID, "overdraft.fee.charged", sourceID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted overdraft fee journal", zap.String("sourceId", sourceID), zap.String("amount", amount.String()))
	return nil
}

// PostFloatDrawn creates a journal entry for a float pool draw (loan disbursement funded by float).
// DR 2100 Borrowings (Float Liability) / CR 1000 Cash
func (s *AccountingService) PostFloatDrawn(ctx context.Context, tenantID, sourceID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "2100")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "FLOAT-DRAW-"+sourceID,
		"Float drawn for loan disbursement "+sourceID, "float.drawn", sourceID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted float drawn journal", zap.String("sourceId", sourceID), zap.String("amount", amount.String()))
	return nil
}

// PostFloatRepaid creates a journal entry for a float pool repayment (collections reducing float liability).
// DR 1000 Cash / CR 2100 Borrowings (Float Liability)
func (s *AccountingService) PostFloatRepaid(ctx context.Context, tenantID, sourceID string, amount decimal.Decimal) error {
	drAccount, err := s.resolveAccountID(ctx, tenantID, "1000")
	if err != nil {
		return err
	}
	crAccount, err := s.resolveAccountID(ctx, tenantID, "2100")
	if err != nil {
		return err
	}

	entry := s.buildSystemEntry(tenantID, "FLOAT-RPMT-"+sourceID,
		"Float repayment from collections "+sourceID, "float.repaid", sourceID,
		drAccount, crAccount, amount)

	if err := s.repo.CreateJournalEntry(ctx, entry); err != nil {
		return err
	}
	s.publisher.PublishJournalPosted(ctx, entry)
	s.logger.Info("Posted float repayment journal", zap.String("sourceId", sourceID), zap.String("amount", amount.String()))
	return nil
}

// --- Private helpers ---

func (s *AccountingService) resolveAccountID(ctx context.Context, tenantID, code string) (uuid.UUID, error) {
	account, err := s.repo.FindAccountByCodeAndTenantIn(ctx, code, []string{tenantID, "system"})
	if err != nil {
		return uuid.Nil, fmt.Errorf("resolve account %s: %w", code, err)
	}
	if account == nil {
		return uuid.Nil, errors.NewBusinessError("GL account not found: " + code)
	}
	return account.ID, nil
}

func (s *AccountingService) buildSystemEntry(tenantID, reference, description, sourceEvent, sourceID string, drAccountID, crAccountID uuid.UUID, amount decimal.Decimal) *model.JournalEntry {
	postedBy := "system"
	entry := &model.JournalEntry{
		TenantID:    tenantID,
		Reference:   reference,
		Description: &description,
		EntryDate:   time.Now(),
		Status:      model.EntryStatusPosted,
		SourceEvent: &sourceEvent,
		SourceID:    &sourceID,
		TotalDebit:  amount,
		TotalCredit: amount,
		PostedBy:    &postedBy,
		Lines: []model.JournalLine{
			{
				AccountID: drAccountID, LineNo: 1, DebitAmount: amount, CreditAmount: decimal.Zero, Currency: "KES",
			},
			{
				AccountID: crAccountID, LineNo: 2, DebitAmount: decimal.Zero, CreditAmount: amount, Currency: "KES",
			},
		},
	}
	return entry
}

func getDecimalFromPayload(m map[string]any, key string) decimal.Decimal {
	if m == nil {
		return decimal.Zero
	}
	v, ok := m[key]
	if !ok || v == nil {
		return decimal.Zero
	}
	switch val := v.(type) {
	case float64:
		return decimal.NewFromFloat(val)
	case string:
		d, err := decimal.NewFromString(val)
		if err != nil {
			return decimal.Zero
		}
		return d
	case json.Number:
		d, err := decimal.NewFromString(val.String())
		if err != nil {
			return decimal.Zero
		}
		return d
	default:
		d, err := decimal.NewFromString(fmt.Sprintf("%v", val))
		if err != nil {
			return decimal.Zero
		}
		return d
	}
}
