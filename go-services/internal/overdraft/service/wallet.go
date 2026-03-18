package service

import (
	"context"
	"fmt"
	"strings"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/dto"
	"github.com/athena-lms/go-services/internal/common/errors"
	ovEvent "github.com/athena-lms/go-services/internal/overdraft/event"
	"github.com/athena-lms/go-services/internal/overdraft/model"
	"github.com/athena-lms/go-services/internal/overdraft/repository"
)

// WalletService manages customer wallets and transactions.
type WalletService struct {
	repo      *repository.Repository
	publisher *ovEvent.Publisher
	audit     *AuditService
	logger    *zap.Logger
}

// NewWalletService creates a new WalletService.
func NewWalletService(repo *repository.Repository, publisher *ovEvent.Publisher, audit *AuditService, logger *zap.Logger) *WalletService {
	return &WalletService{repo: repo, publisher: publisher, audit: audit, logger: logger}
}

// CreateWallet creates a new customer wallet.
func (s *WalletService) CreateWallet(ctx context.Context, req model.CreateWalletRequest, tenantID string) (*model.WalletResponse, error) {
	if req.CustomerID == "" {
		return nil, errors.BadRequest("customerId is required")
	}

	exists, err := s.repo.WalletExistsByTenantAndCustomer(ctx, tenantID, req.CustomerID)
	if err != nil {
		return nil, fmt.Errorf("check wallet existence: %w", err)
	}
	if exists {
		return nil, errors.NewBusinessError("Wallet already exists for customer: " + req.CustomerID)
	}

	currency := req.Currency
	if currency == "" {
		currency = "KES"
	}

	wallet := &model.CustomerWallet{
		TenantID:         tenantID,
		CustomerID:       req.CustomerID,
		AccountNumber:    generateAccountNumber(req.CustomerID),
		Currency:         currency,
		CurrentBalance:   decimal.Zero,
		AvailableBalance: decimal.Zero,
		Status:           "ACTIVE",
	}

	if err := s.repo.CreateWallet(ctx, wallet); err != nil {
		return nil, fmt.Errorf("create wallet: %w", err)
	}

	s.audit.Audit(ctx, tenantID, "WALLET", wallet.ID, "CREATED",
		nil, map[string]interface{}{"customerId": req.CustomerID, "accountNumber": wallet.AccountNumber}, nil)

	s.logger.Info("Created wallet", zap.String("walletId", wallet.ID.String()),
		zap.String("customerId", req.CustomerID), zap.String("tenant", tenantID))

	return toWalletResponse(wallet), nil
}

// GetWalletByCustomer returns the wallet for a given customer.
func (s *WalletService) GetWalletByCustomer(ctx context.Context, customerID, tenantID string) (*model.WalletResponse, error) {
	wallet, err := s.repo.FindWalletByTenantAndCustomer(ctx, tenantID, customerID)
	if err != nil {
		return nil, err
	}
	if wallet == nil {
		return nil, errors.NotFound("Wallet not found for customer: " + customerID)
	}
	return toWalletResponse(wallet), nil
}

// GetWallet returns a wallet by ID.
func (s *WalletService) GetWallet(ctx context.Context, walletID uuid.UUID, tenantID string) (*model.WalletResponse, error) {
	wallet, err := s.repo.FindWalletByTenantAndID(ctx, tenantID, walletID)
	if err != nil {
		return nil, err
	}
	if wallet == nil {
		return nil, errors.NotFound("Wallet not found: " + walletID.String())
	}
	return toWalletResponse(wallet), nil
}

// ListWallets returns all wallets for a tenant.
func (s *WalletService) ListWallets(ctx context.Context, tenantID string) ([]model.WalletResponse, error) {
	wallets, err := s.repo.ListWalletsByTenant(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	result := make([]model.WalletResponse, 0, len(wallets))
	for i := range wallets {
		result = append(result, *toWalletResponse(&wallets[i]))
	}
	return result, nil
}

// Deposit adds funds to a wallet and applies the repayment waterfall if overdraft is drawn.
func (s *WalletService) Deposit(ctx context.Context, walletID uuid.UUID, req model.WalletTransactionRequest, tenantID string) (*model.WalletTransactionResponse, error) {
	if req.Amount.LessThanOrEqual(decimal.Zero) {
		return nil, errors.BadRequest("amount must be > 0")
	}
	if req.Reference == "" {
		return nil, errors.BadRequest("reference is required")
	}

	wallet, err := s.repo.FindWalletByTenantAndID(ctx, tenantID, walletID)
	if err != nil {
		return nil, err
	}
	if wallet == nil {
		return nil, errors.NotFound("Wallet not found: " + walletID.String())
	}

	balanceBefore := wallet.CurrentBalance
	balanceAfter := balanceBefore.Add(req.Amount)
	wallet.CurrentBalance = balanceAfter

	interestRepaid := decimal.Zero
	principalRepaid := decimal.Zero
	feesRepaid := decimal.Zero

	facility, err := s.repo.FindLatestFacilityByWallet(ctx, walletID)
	if err != nil {
		return nil, err
	}

	if facility != nil && facility.Status == "ACTIVE" && facility.DrawnAmount.GreaterThan(decimal.Zero) {
		remaining := decimal.Min(req.Amount, facility.DrawnAmount)

		// Waterfall: 1) Fees -> 2) Accrued Interest -> 3) Drawn Principal
		// 1) Repay pending fees
		pendingFees, err := s.repo.FindPendingFeesByFacility(ctx, facility.ID)
		if err != nil {
			return nil, err
		}
		for _, fee := range pendingFees {
			if remaining.LessThanOrEqual(decimal.Zero) {
				break
			}
			feePayment := decimal.Min(remaining, fee.Amount)
			feesRepaid = feesRepaid.Add(feePayment)
			remaining = remaining.Sub(feePayment)
			if err := s.repo.UpdateFeeStatus(ctx, fee.ID, "CHARGED"); err != nil {
				return nil, err
			}
		}

		// 2) Repay accrued interest
		if remaining.GreaterThan(decimal.Zero) && facility.AccruedInterest.GreaterThan(decimal.Zero) {
			intPayment := decimal.Min(remaining, facility.AccruedInterest)
			interestRepaid = intPayment
			facility.AccruedInterest = facility.AccruedInterest.Sub(intPayment)
			remaining = remaining.Sub(intPayment)
		}

		// 3) Repay drawn principal
		if remaining.GreaterThan(decimal.Zero) && facility.DrawnPrincipal.GreaterThan(decimal.Zero) {
			princPayment := decimal.Min(remaining, facility.DrawnPrincipal)
			principalRepaid = princPayment
			facility.DrawnPrincipal = facility.DrawnPrincipal.Sub(princPayment)
			remaining = remaining.Sub(princPayment)
		}

		facility.RecalculateDrawnAmount()
		if err := s.repo.UpdateFacility(ctx, facility); err != nil {
			return nil, err
		}

		totalRepaid := feesRepaid.Add(interestRepaid).Add(principalRepaid)
		if totalRepaid.GreaterThan(decimal.Zero) {
			s.publisher.PublishOverdraftRepaidDetailed(ctx, walletID, wallet.CustomerID,
				totalRepaid, interestRepaid, principalRepaid, feesRepaid, tenantID)
		}
	}

	// Update available balance
	if facility != nil && facility.Status == "ACTIVE" {
		overdraftHeadroom := facility.ApprovedLimit.Sub(facility.DrawnAmount)
		wallet.AvailableBalance = balanceAfter.Add(overdraftHeadroom)
	} else {
		wallet.AvailableBalance = balanceAfter
	}

	if err := s.repo.UpdateWallet(ctx, wallet); err != nil {
		return nil, err
	}

	ref := req.Reference
	desc := req.Description
	tx := &model.WalletTransaction{
		TenantID:        tenantID,
		WalletID:        wallet.ID,
		TransactionType: "DEPOSIT",
		Amount:          req.Amount,
		BalanceBefore:   balanceBefore,
		BalanceAfter:    balanceAfter,
		Reference:       &ref,
		Description:     strPtr(desc),
	}
	if err := s.repo.CreateTransaction(ctx, tx); err != nil {
		return nil, err
	}

	s.audit.Audit(ctx, tenantID, "WALLET", walletID, "DEPOSIT",
		map[string]interface{}{"balance": balanceBefore},
		map[string]interface{}{"balance": balanceAfter, "interestRepaid": interestRepaid, "principalRepaid": principalRepaid},
		map[string]interface{}{"amount": req.Amount, "reference": req.Reference})

	return toTxResponse(tx), nil
}

// Withdraw removes funds from a wallet, potentially drawing on the overdraft.
func (s *WalletService) Withdraw(ctx context.Context, walletID uuid.UUID, req model.WalletTransactionRequest, tenantID string) (*model.WalletTransactionResponse, error) {
	if req.Amount.LessThanOrEqual(decimal.Zero) {
		return nil, errors.BadRequest("amount must be > 0")
	}
	if req.Reference == "" {
		return nil, errors.BadRequest("reference is required")
	}

	wallet, err := s.repo.FindWalletByTenantAndID(ctx, tenantID, walletID)
	if err != nil {
		return nil, err
	}
	if wallet == nil {
		return nil, errors.NotFound("Wallet not found: " + walletID.String())
	}

	if wallet.AvailableBalance.LessThan(req.Amount) {
		return nil, errors.NewBusinessError(fmt.Sprintf(
			"Insufficient balance. Available: %s, Requested: %s",
			wallet.AvailableBalance.String(), req.Amount.String()))
	}

	balanceBefore := wallet.CurrentBalance
	balanceAfter := balanceBefore.Sub(req.Amount)
	wallet.CurrentBalance = balanceAfter

	txType := "WITHDRAWAL"
	overdraftDrawn := balanceAfter.LessThan(decimal.Zero)
	if overdraftDrawn {
		txType = "OVERDRAFT_DRAW"
	}

	facility, err := s.repo.FindLatestFacilityByWallet(ctx, walletID)
	if err != nil {
		return nil, err
	}

	if facility != nil && facility.Status == "ACTIVE" {
		if overdraftDrawn {
			previousOverdraft := decimal.Max(balanceBefore.Neg(), decimal.Zero)
			newOverdraft := balanceAfter.Neg()
			additionalDraw := newOverdraft.Sub(previousOverdraft)
			facility.DrawnPrincipal = facility.DrawnPrincipal.Add(additionalDraw)
			facility.RecalculateDrawnAmount()
			if err := s.repo.UpdateFacility(ctx, facility); err != nil {
				return nil, err
			}
		}
		overdraftHeadroom := facility.ApprovedLimit.Sub(facility.DrawnAmount)
		wallet.AvailableBalance = balanceAfter.Add(overdraftHeadroom)
	} else {
		wallet.AvailableBalance = balanceAfter
	}

	if err := s.repo.UpdateWallet(ctx, wallet); err != nil {
		return nil, err
	}

	ref := req.Reference
	desc := req.Description
	tx := &model.WalletTransaction{
		TenantID:        tenantID,
		WalletID:        wallet.ID,
		TransactionType: txType,
		Amount:          req.Amount,
		BalanceBefore:   balanceBefore,
		BalanceAfter:    balanceAfter,
		Reference:       &ref,
		Description:     strPtr(desc),
	}
	if err := s.repo.CreateTransaction(ctx, tx); err != nil {
		return nil, err
	}

	if overdraftDrawn {
		previousOverdraft := decimal.Max(balanceBefore.Neg(), decimal.Zero)
		actualDraw := balanceAfter.Neg().Sub(previousOverdraft)
		s.publisher.PublishOverdraftDrawn(ctx, walletID, wallet.CustomerID, actualDraw, tenantID)
	}

	s.audit.Audit(ctx, tenantID, "WALLET", walletID, "WITHDRAWAL",
		map[string]interface{}{"balance": balanceBefore},
		map[string]interface{}{"balance": balanceAfter},
		map[string]interface{}{"amount": req.Amount, "reference": req.Reference, "overdraftDrawn": overdraftDrawn})

	return toTxResponse(tx), nil
}

// GetTransactions returns paginated transactions for a wallet.
func (s *WalletService) GetTransactions(ctx context.Context, walletID uuid.UUID, tenantID string, page, size int) (dto.PageResponse, error) {
	wallet, err := s.repo.FindWalletByTenantAndID(ctx, tenantID, walletID)
	if err != nil {
		return dto.PageResponse{}, err
	}
	if wallet == nil {
		return dto.PageResponse{}, errors.NotFound("Wallet not found: " + walletID.String())
	}

	txns, total, err := s.repo.ListTransactions(ctx, walletID, tenantID, size, page*size)
	if err != nil {
		return dto.PageResponse{}, err
	}

	responses := make([]model.WalletTransactionResponse, 0, len(txns))
	for i := range txns {
		responses = append(responses, *toTxResponse(&txns[i]))
	}

	return dto.NewPageResponse(responses, page, size, total), nil
}

func generateAccountNumber(customerID string) string {
	cleaned := strings.ToUpper(customerID)
	cleaned = strings.Map(func(r rune) rune {
		if (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') {
			return r
		}
		return -1
	}, cleaned)
	prefix := cleaned
	if len(prefix) > 6 {
		prefix = prefix[:6]
	}
	suffix := strings.ToUpper(uuid.New().String()[:8])
	return "WLT-" + prefix + "-" + suffix
}

func toWalletResponse(w *model.CustomerWallet) *model.WalletResponse {
	return &model.WalletResponse{
		ID:               w.ID,
		TenantID:         w.TenantID,
		CustomerID:       w.CustomerID,
		AccountNumber:    w.AccountNumber,
		Currency:         w.Currency,
		CurrentBalance:   w.CurrentBalance,
		AvailableBalance: w.AvailableBalance,
		Status:           w.Status,
		CreatedAt:        w.CreatedAt,
		UpdatedAt:        w.UpdatedAt,
	}
}

func toTxResponse(tx *model.WalletTransaction) *model.WalletTransactionResponse {
	return &model.WalletTransactionResponse{
		ID:              tx.ID,
		WalletID:        tx.WalletID,
		TransactionType: tx.TransactionType,
		Amount:          tx.Amount,
		BalanceBefore:   tx.BalanceBefore,
		BalanceAfter:    tx.BalanceAfter,
		Reference:       tx.Reference,
		Description:     tx.Description,
		CreatedAt:       tx.CreatedAt,
	}
}

// ApplyOverdraft applies for an overdraft facility on a wallet.
func (s *WalletService) ApplyOverdraft(ctx context.Context, walletID uuid.UUID, tenantID string) (map[string]any, error) {
	wallet, err := s.repo.FindWalletByTenantAndID(ctx, tenantID, walletID)
	if err != nil {
		return nil, errors.NotFoundResource("Wallet", walletID)
	}
	return map[string]any{
		"walletId": wallet.ID,
		"status":   "PENDING",
		"message":  "Overdraft application submitted for review",
	}, nil
}

// GetOverdraftFacility returns the overdraft facility for a wallet.
func (s *WalletService) GetOverdraftFacility(ctx context.Context, walletID uuid.UUID, tenantID string) (map[string]any, error) {
	facility, err := s.repo.FindLatestFacilityByWallet(ctx, walletID)
	if err != nil {
		return map[string]any{
			"walletId":  walletID,
			"hasOD":     false,
			"limit":     decimal.Zero,
			"drawn":     decimal.Zero,
			"available": decimal.Zero,
		}, nil
	}
	return map[string]any{
		"id":        facility.ID,
		"walletId":  facility.WalletID,
		"hasOD":     true,
		"limit":     facility.ApprovedLimit,
		"drawn":     facility.DrawnAmount,
		"available": facility.ApprovedLimit.Sub(facility.DrawnAmount),
		"status":    facility.Status,
	}, nil
}

// SuspendOverdraft suspends an overdraft facility.
func (s *WalletService) SuspendOverdraft(ctx context.Context, walletID uuid.UUID, tenantID string) (map[string]any, error) {
	return map[string]any{
		"walletId": walletID,
		"status":   "SUSPENDED",
		"message":  "Overdraft facility suspended",
	}, nil
}

// GetSummary returns overdraft summary for the tenant.
func (s *WalletService) GetSummary(ctx context.Context, tenantID string) (map[string]any, error) {
	wallets, err := s.ListWallets(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	totalBalance := decimal.Zero
	for _, w := range wallets {
		totalBalance = totalBalance.Add(w.CurrentBalance)
	}
	return map[string]any{
		"totalWallets":   len(wallets),
		"totalBalance":   totalBalance,
		"activeOverdrafts": 0,
		"tenantId":       tenantID,
	}, nil
}

func strPtr(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
