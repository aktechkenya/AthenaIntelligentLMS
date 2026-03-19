package service

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/account/model"
	"github.com/athena-lms/go-services/internal/account/repository"
	"github.com/athena-lms/go-services/internal/common/errors"
)

// EODService orchestrates end-of-day processing with proper audit trail,
// idempotency, concurrency protection, and detailed error reporting.
type EODService struct {
	repo        *repository.Repository
	interestSvc *InterestService
	dormancySvc *DormancyService
	logger      *zap.Logger
}

// NewEODService creates a new EODService.
func NewEODService(interestSvc *InterestService, dormancySvc *DormancyService, logger *zap.Logger) *EODService {
	return &EODService{
		repo:        interestSvc.repo,
		interestSvc: interestSvc,
		dormancySvc: dormancySvc,
		logger:      logger,
	}
}

// EODStepError captures a per-account error during a batch step.
type EODStepError struct {
	Step      string `json:"step"`
	AccountID string `json:"accountId"`
	Error     string `json:"error"`
}

// RunEOD executes the full end-of-day batch with proper controls:
//  1. Acquire advisory lock (prevent concurrent runs)
//  2. Check idempotency (skip if already run today and completed)
//  3. Create audit record (eod_runs table)
//  4. Execute steps: accrue interest → detect dormancy → process maturities
//  5. Record detailed results and release lock
func (s *EODService) RunEOD(ctx context.Context, tenantID, initiatedBy string) (*model.EODRun, error) {
	today := time.Now().Truncate(24 * time.Hour)
	dateStr := today.Format("2006-01-02")

	// ── Step 1: Advisory lock ────────────────────────────────────────────
	acquired, err := s.repo.AcquireEODLock(ctx, tenantID)
	if err != nil {
		return nil, fmt.Errorf("acquire EOD lock: %w", err)
	}
	if !acquired {
		return nil, errors.NewBusinessError("EOD is already running for this tenant. Please wait for it to complete.")
	}
	defer func() {
		if err := s.repo.ReleaseEODLock(ctx, tenantID); err != nil {
			s.logger.Error("Failed to release EOD lock", zap.Error(err))
		}
	}()

	// ── Step 2: Idempotency check ───────────────────────────────────────
	existing, err := s.repo.GetEODRunForDate(ctx, tenantID, today)
	if err != nil {
		return nil, fmt.Errorf("check existing EOD run: %w", err)
	}
	if existing != nil && existing.Status == "COMPLETED" {
		s.logger.Info("EOD already completed for today, returning existing result",
			zap.String("date", dateStr))
		return existing, nil
	}
	// If previous run was PARTIAL or FAILED, allow re-run
	if existing != nil && existing.Status == "RUNNING" {
		return nil, errors.NewBusinessError("A previous EOD run is still in RUNNING state. Investigate before re-running.")
	}

	// ── Step 3: Create audit record ─────────────────────────────────────
	run := &model.EODRun{
		TenantID:    tenantID,
		RunDate:     today,
		Status:      "RUNNING",
		InitiatedBy: initiatedBy,
	}

	if existing != nil {
		// Re-running a failed/partial — update existing record
		run = existing
		run.Status = "RUNNING"
		run.InitiatedBy = initiatedBy
		run.AccountsAccrued = 0
		run.AccrualErrors = 0
		run.DormantDetected = 0
		run.DormancyErrors = 0
		run.MaturedProcessed = 0
		run.MaturityErrors = 0
		run.InterestPostedCount = 0
		run.PostingErrors = 0
		run.TotalInterestAccrued = decimal.Zero
		run.TotalInterestPosted = decimal.Zero
		run.TotalWHTDeducted = decimal.Zero
	} else {
		if err := s.repo.CreateEODRun(ctx, run); err != nil {
			return nil, fmt.Errorf("create EOD run record: %w", err)
		}
	}

	s.logger.Info("Starting EOD run",
		zap.String("date", dateStr),
		zap.String("tenantId", tenantID),
		zap.String("initiatedBy", initiatedBy),
		zap.String("runId", run.ID.String()))

	var stepErrors []EODStepError
	allStepsOK := true

	// ── Step 4a: Accrue interest ────────────────────────────────────────
	accrued, accrualErrs, totalAccrued, err := s.accrueInterestStep(ctx, tenantID, today)
	run.AccountsAccrued = accrued
	run.AccrualErrors = len(accrualErrs)
	run.TotalInterestAccrued = totalAccrued
	stepErrors = append(stepErrors, accrualErrs...)
	if err != nil {
		s.logger.Error("Interest accrual step failed", zap.Error(err))
		allStepsOK = false
	}

	// ── Step 4b: Detect dormancy ────────────────────────────────────────
	dormant, dormancyErrs, err := s.detectDormancyStep(ctx, tenantID)
	run.DormantDetected = dormant
	run.DormancyErrors = len(dormancyErrs)
	stepErrors = append(stepErrors, dormancyErrs...)
	if err != nil {
		s.logger.Error("Dormancy detection step failed", zap.Error(err))
		allStepsOK = false
	}

	// ── Step 4c: Process matured fixed deposits ─────────────────────────
	matured, maturityErrs, totalPosted, totalWHT, err := s.processMaturityStep(ctx, tenantID, today)
	run.MaturedProcessed = matured
	run.MaturityErrors = len(maturityErrs)
	run.TotalInterestPosted = totalPosted
	run.TotalWHTDeducted = totalWHT
	stepErrors = append(stepErrors, maturityErrs...)
	if err != nil {
		s.logger.Error("Maturity processing step failed", zap.Error(err))
		allStepsOK = false
	}

	// ── Step 5: Record results ──────────────────────────────────────────
	if allStepsOK && len(stepErrors) == 0 {
		run.Status = "COMPLETED"
	} else if accrued > 0 || dormant > 0 || matured > 0 {
		run.Status = "PARTIAL"
	} else {
		run.Status = "FAILED"
	}

	if len(stepErrors) > 0 {
		errJSON, _ := json.Marshal(stepErrors)
		errStr := string(errJSON)
		run.ErrorDetails = &errStr
	}

	if err := s.repo.UpdateEODRun(ctx, run); err != nil {
		s.logger.Error("Failed to update EOD run record", zap.Error(err))
	}

	s.logger.Info("EOD run finished",
		zap.String("date", dateStr),
		zap.String("status", run.Status),
		zap.String("runId", run.ID.String()),
		zap.Int("accrued", run.AccountsAccrued),
		zap.Int("accrualErrors", run.AccrualErrors),
		zap.Int("dormant", run.DormantDetected),
		zap.Int("matured", run.MaturedProcessed),
		zap.String("totalInterestAccrued", run.TotalInterestAccrued.String()))

	return run, nil
}

// GetEODHistory returns recent EOD runs.
func (s *EODService) GetEODHistory(ctx context.Context, tenantID string, limit int) ([]*model.EODRun, error) {
	if limit <= 0 {
		limit = 30
	}
	return s.repo.ListEODRuns(ctx, tenantID, limit)
}

// ─── Step implementations ───────────────────────────────────────────────────

func (s *EODService) accrueInterestStep(ctx context.Context, tenantID string, date time.Time) (int, []EODStepError, decimal.Decimal, error) {
	accounts, err := s.repo.ListAccountsEligibleForInterest(ctx, tenantID)
	if err != nil {
		return 0, nil, decimal.Zero, fmt.Errorf("list eligible accounts: %w", err)
	}

	accrued := 0
	totalAccrued := decimal.Zero
	var stepErrors []EODStepError

	for _, account := range accounts {
		// Idempotency: skip if already accrued today
		already, err := s.repo.HasAccrualForDate(ctx, account.ID, date)
		if err != nil {
			stepErrors = append(stepErrors, EODStepError{
				Step: "accrual", AccountID: account.ID.String(),
				Error: "check existing accrual: " + err.Error(),
			})
			continue
		}
		if already {
			accrued++ // count as success (already done)
			continue
		}

		dailyAmount, err := s.interestSvc.accrueForAccount(ctx, account, date)
		if err != nil {
			stepErrors = append(stepErrors, EODStepError{
				Step: "accrual", AccountID: account.ID.String(), Error: err.Error(),
			})
			continue
		}
		if dailyAmount.GreaterThan(decimal.Zero) {
			totalAccrued = totalAccrued.Add(dailyAmount)
		}
		accrued++
	}

	return accrued, stepErrors, totalAccrued, nil
}

func (s *EODService) detectDormancyStep(ctx context.Context, tenantID string) (int, []EODStepError, error) {
	accounts, err := s.repo.ListDormancyCandidates(ctx, tenantID, 365)
	if err != nil {
		return 0, nil, fmt.Errorf("list dormancy candidates: %w", err)
	}

	marked := 0
	var stepErrors []EODStepError
	for _, account := range accounts {
		if err := s.repo.SetAccountDormant(ctx, account.ID); err != nil {
			stepErrors = append(stepErrors, EODStepError{
				Step: "dormancy", AccountID: account.ID.String(), Error: err.Error(),
			})
			continue
		}
		marked++
	}
	return marked, stepErrors, nil
}

func (s *EODService) processMaturityStep(ctx context.Context, tenantID string, date time.Time) (int, []EODStepError, decimal.Decimal, decimal.Decimal, error) {
	accounts, err := s.repo.ListMaturedFixedDeposits(ctx, tenantID, date)
	if err != nil {
		return 0, nil, decimal.Zero, decimal.Zero, fmt.Errorf("list matured FDs: %w", err)
	}

	processed := 0
	totalPosted := decimal.Zero
	totalWHT := decimal.Zero
	var stepErrors []EODStepError

	for _, account := range accounts {
		// Post pending interest
		posting, err := s.interestSvc.PostAccruedInterest(ctx, account.ID, tenantID, "EOD_BATCH")
		if err != nil {
			// Non-fatal — might have no interest to post
			if err.Error() != "No unposted interest to post" {
				stepErrors = append(stepErrors, EODStepError{
					Step: "maturity_interest", AccountID: account.ID.String(), Error: err.Error(),
				})
			}
		} else if posting != nil {
			totalPosted = totalPosted.Add(posting.NetInterest)
			totalWHT = totalWHT.Add(posting.WithholdingTax)
		}

		if account.AutoRenew && account.TermDays != nil {
			newMaturity := time.Now().AddDate(0, 0, *account.TermDays)
			if err := s.repo.UpdateAccountForFDMaturity(ctx, account.ID, model.AccountStatusActive, &newMaturity); err != nil {
				stepErrors = append(stepErrors, EODStepError{
					Step: "maturity_renew", AccountID: account.ID.String(), Error: err.Error(),
				})
				continue
			}
		} else {
			if err := s.repo.UpdateAccountForFDMaturity(ctx, account.ID, model.AccountStatusMatured, nil); err != nil {
				stepErrors = append(stepErrors, EODStepError{
					Step: "maturity_close", AccountID: account.ID.String(), Error: err.Error(),
				})
				continue
			}
		}
		processed++
	}

	return processed, stepErrors, totalPosted, totalWHT, nil
}
