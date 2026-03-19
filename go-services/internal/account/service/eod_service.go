package service

import (
	"context"
	"time"

	"go.uber.org/zap"
)

// EODService orchestrates end-of-day processing.
type EODService struct {
	interestSvc *InterestService
	dormancySvc *DormancyService
	logger      *zap.Logger
}

// NewEODService creates a new EODService.
func NewEODService(interestSvc *InterestService, dormancySvc *DormancyService, logger *zap.Logger) *EODService {
	return &EODService{interestSvc: interestSvc, dormancySvc: dormancySvc, logger: logger}
}

// EODResult holds the results of an EOD run.
type EODResult struct {
	Date             string `json:"date"`
	AccountsAccrued  int    `json:"accountsAccrued"`
	DormantDetected  int    `json:"dormantDetected"`
	MaturedProcessed int    `json:"maturedProcessed"`
	Status           string `json:"status"`
}

// RunEOD executes the full end-of-day batch: accrue interest, detect dormancy, process maturities.
func (s *EODService) RunEOD(ctx context.Context, tenantID string) (*EODResult, error) {
	date := time.Now()
	dateStr := date.Format("2006-01-02")
	s.logger.Info("Starting EOD run", zap.String("date", dateStr), zap.String("tenantId", tenantID))

	result := &EODResult{
		Date:   dateStr,
		Status: "COMPLETED",
	}

	// 1. Accrue interest
	accrued, err := s.interestSvc.AccrueInterestForDate(ctx, tenantID, date)
	if err != nil {
		s.logger.Error("Interest accrual failed during EOD", zap.Error(err))
		result.Status = "PARTIAL"
	}
	result.AccountsAccrued = accrued

	// 2. Detect dormancy (using default 365-day threshold)
	dormant, err := s.dormancySvc.DetectDormantAccounts(ctx, tenantID, 365)
	if err != nil {
		s.logger.Error("Dormancy detection failed during EOD", zap.Error(err))
		result.Status = "PARTIAL"
	}
	result.DormantDetected = dormant

	// 3. Process matured fixed deposits
	matured, err := s.processMaturedDeposits(ctx, tenantID, date)
	if err != nil {
		s.logger.Error("Maturity processing failed during EOD", zap.Error(err))
		result.Status = "PARTIAL"
	}
	result.MaturedProcessed = matured

	s.logger.Info("EOD run completed",
		zap.String("date", dateStr),
		zap.String("status", result.Status),
		zap.Int("accrued", accrued),
		zap.Int("dormant", dormant),
		zap.Int("matured", matured))

	return result, nil
}

func (s *EODService) processMaturedDeposits(ctx context.Context, tenantID string, date time.Time) (int, error) {
	repo := s.interestSvc.repo
	accounts, err := repo.ListMaturedFixedDeposits(ctx, tenantID, date)
	if err != nil {
		return 0, err
	}

	processed := 0
	for _, account := range accounts {
		// Post any pending interest first
		_, _ = s.interestSvc.PostAccruedInterest(ctx, account.ID, tenantID, "EOD_BATCH")

		if account.AutoRenew && account.TermDays != nil {
			// Auto-renew: extend maturity
			newMaturity := time.Now().AddDate(0, 0, *account.TermDays)
			if err := repo.UpdateAccountForFDMaturity(ctx, account.ID, "ACTIVE", &newMaturity); err != nil {
				s.logger.Warn("Failed to renew FD", zap.String("accountId", account.ID.String()), zap.Error(err))
				continue
			}
		} else {
			// Mark as matured
			if err := repo.UpdateAccountForFDMaturity(ctx, account.ID, "MATURED", nil); err != nil {
				s.logger.Warn("Failed to mature FD", zap.String("accountId", account.ID.String()), zap.Error(err))
				continue
			}
		}
		processed++
	}
	return processed, nil
}
