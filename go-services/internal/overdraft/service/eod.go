package service

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	ovEvent "github.com/athena-lms/go-services/internal/overdraft/event"
	"github.com/athena-lms/go-services/internal/overdraft/model"
	"github.com/athena-lms/go-services/internal/overdraft/repository"
)

// EODService handles End-of-Day batch processing for regulatory compliance.
// Implements IFRS 9 daily interest accrual, DPD recalculation, NPL staging,
// and monthly billing statement generation.
type EODService struct {
	repo      *repository.Repository
	publisher *ovEvent.Publisher
	audit     *AuditService
	logger    *zap.Logger

	mu        sync.Mutex
	running   bool
	lastRunAt *time.Time
	lastError error
}

// NewEODService creates a new EOD batch service.
func NewEODService(repo *repository.Repository, publisher *ovEvent.Publisher, audit *AuditService, logger *zap.Logger) *EODService {
	return &EODService{repo: repo, publisher: publisher, audit: audit, logger: logger}
}

// EODResult captures the results of an EOD batch run.
type EODResult struct {
	RunDate              time.Time `json:"runDate"`
	FacilitiesProcessed  int       `json:"facilitiesProcessed"`
	InterestChargesCreated int     `json:"interestChargesCreated"`
	TotalInterestAccrued string    `json:"totalInterestAccrued"`
	DPDUpdates           int       `json:"dpdUpdates"`
	StageChanges         int       `json:"stageChanges"`
	BillingStatements    int       `json:"billingStatements"`
	Errors               int       `json:"errors"`
	DurationMs           int64     `json:"durationMs"`
}

// IsRunning returns whether an EOD batch is currently executing.
func (s *EODService) IsRunning() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.running
}

// LastRunStatus returns status of the last EOD run.
func (s *EODService) LastRunStatus() map[string]interface{} {
	s.mu.Lock()
	defer s.mu.Unlock()
	status := map[string]interface{}{
		"running": s.running,
	}
	if s.lastRunAt != nil {
		status["lastRunAt"] = s.lastRunAt.Format(time.RFC3339)
	}
	if s.lastError != nil {
		status["lastError"] = s.lastError.Error()
	}
	return status
}

// RunEOD executes the full end-of-day batch for the given date.
// This is idempotent — re-running for the same date will skip already-processed items.
func (s *EODService) RunEOD(ctx context.Context, runDate time.Time) (*EODResult, error) {
	s.mu.Lock()
	if s.running {
		s.mu.Unlock()
		return nil, fmt.Errorf("EOD batch already running")
	}
	s.running = true
	s.mu.Unlock()

	defer func() {
		s.mu.Lock()
		s.running = false
		now := time.Now()
		s.lastRunAt = &now
		s.mu.Unlock()
	}()

	start := time.Now()
	chargeDate := time.Date(runDate.Year(), runDate.Month(), runDate.Day(), 0, 0, 0, 0, time.UTC)

	result := &EODResult{RunDate: chargeDate}

	s.logger.Info("EOD batch starting", zap.Time("runDate", chargeDate))

	// Step 1: Get all active facilities with drawn amounts
	facilities, err := s.repo.FindActiveDrawnFacilities(ctx)
	if err != nil {
		s.lastError = err
		return nil, fmt.Errorf("fetch active facilities: %w", err)
	}

	result.FacilitiesProcessed = len(facilities)
	s.logger.Info("EOD: found active drawn facilities", zap.Int("count", len(facilities)))

	totalInterest := decimal.Zero
	daysInYear := decimal.NewFromInt(365)

	for i := range facilities {
		f := &facilities[i]

		// Step 2: Daily interest accrual (IFRS 9 EIR method - simple daily for overdrafts)
		interestCharged, err := s.accrueInterest(ctx, f, chargeDate, daysInYear)
		if err != nil {
			s.logger.Error("EOD: interest accrual failed",
				zap.String("facilityId", f.ID.String()), zap.Error(err))
			result.Errors++
			continue
		}
		if interestCharged.GreaterThan(decimal.Zero) {
			result.InterestChargesCreated++
			totalInterest = totalInterest.Add(interestCharged)
		}

		// Step 3: DPD recalculation and NPL staging
		stageChanged, err := s.updateDPDAndStage(ctx, f, chargeDate)
		if err != nil {
			s.logger.Error("EOD: DPD update failed",
				zap.String("facilityId", f.ID.String()), zap.Error(err))
			result.Errors++
			continue
		}
		result.DPDUpdates++
		if stageChanged {
			result.StageChanges++
		}

		// Step 4: Monthly billing statement generation (on billing date)
		generated, err := s.generateBillingIfDue(ctx, f, chargeDate)
		if err != nil {
			s.logger.Error("EOD: billing generation failed",
				zap.String("facilityId", f.ID.String()), zap.Error(err))
			result.Errors++
			continue
		}
		if generated {
			result.BillingStatements++
		}
	}

	result.TotalInterestAccrued = totalInterest.StringFixed(4)
	result.DurationMs = time.Since(start).Milliseconds()

	s.mu.Lock()
	s.lastError = nil
	s.mu.Unlock()

	s.logger.Info("EOD batch completed",
		zap.Int("facilities", result.FacilitiesProcessed),
		zap.Int("interestCharges", result.InterestChargesCreated),
		zap.String("totalInterest", result.TotalInterestAccrued),
		zap.Int("dpdUpdates", result.DPDUpdates),
		zap.Int("stageChanges", result.StageChanges),
		zap.Int("billingStatements", result.BillingStatements),
		zap.Int("errors", result.Errors),
		zap.Int64("durationMs", result.DurationMs))

	return result, nil
}

// accrueInterest calculates and records daily interest for a facility.
// Uses simple daily accrual: interest = drawn_principal * (annual_rate / 365)
// Idempotent: skips if charge already exists for this facility+date.
func (s *EODService) accrueInterest(ctx context.Context, f *model.OverdraftFacility, chargeDate time.Time, daysInYear decimal.Decimal) (decimal.Decimal, error) {
	// Idempotency check — prevent double-charging
	exists, err := s.repo.InterestChargeExists(ctx, f.ID, chargeDate)
	if err != nil {
		return decimal.Zero, fmt.Errorf("check interest existence: %w", err)
	}
	if exists {
		s.logger.Debug("EOD: interest already charged, skipping",
			zap.String("facilityId", f.ID.String()),
			zap.Time("chargeDate", chargeDate))
		return decimal.Zero, nil
	}

	// Only accrue on drawn principal (not on accrued interest — simple interest model)
	if f.DrawnPrincipal.LessThanOrEqual(decimal.Zero) {
		return decimal.Zero, nil
	}

	// Daily rate = annual rate / 365
	// InterestRate is stored as decimal (e.g. 0.15 = 15% p.a.)
	dailyRate := f.InterestRate.Div(daysInYear)
	interestAmount := f.DrawnPrincipal.Mul(dailyRate).Round(4)

	if interestAmount.LessThanOrEqual(decimal.Zero) {
		return decimal.Zero, nil
	}

	// Create interest charge record
	reference := fmt.Sprintf("INT-%s-%s", f.ID.String()[:8], chargeDate.Format("20060102"))
	charge := &model.OverdraftInterestCharge{
		TenantID:        f.TenantID,
		FacilityID:      f.ID,
		ChargeDate:      chargeDate,
		DrawnAmount:     f.DrawnPrincipal,
		DailyRate:       dailyRate,
		InterestCharged: interestAmount,
		Reference:       reference,
	}
	if err := s.repo.CreateInterestCharge(ctx, charge); err != nil {
		return decimal.Zero, fmt.Errorf("create interest charge: %w", err)
	}

	// Update facility accrued interest
	beforeInterest := f.AccruedInterest
	f.AccruedInterest = f.AccruedInterest.Add(interestAmount)
	f.RecalculateDrawnAmount()
	if err := s.repo.UpdateFacility(ctx, f); err != nil {
		return decimal.Zero, fmt.Errorf("update facility interest: %w", err)
	}

	// Publish event for accounting service (creates GL entry: DR 1250 / CR 4300)
	wallet, _ := s.repo.FindWalletByID(ctx, f.WalletID)
	if wallet != nil {
		s.publisher.PublishInterestCharged(ctx, f.WalletID, wallet.CustomerID, interestAmount, f.TenantID)

		// Update wallet available balance (headroom reduced by interest)
		if f.Status == "ACTIVE" {
			overdraftHeadroom := f.ApprovedLimit.Sub(f.DrawnAmount)
			wallet.AvailableBalance = wallet.CurrentBalance.Add(overdraftHeadroom)
			s.repo.UpdateWallet(ctx, wallet)
		}
	}

	// Audit trail
	s.audit.Audit(ctx, f.TenantID, "INTEREST", f.ID, "INTEREST_ACCRUED",
		map[string]interface{}{"accruedInterest": beforeInterest.String()},
		map[string]interface{}{"accruedInterest": f.AccruedInterest.String(), "drawnAmount": f.DrawnAmount.String()},
		map[string]interface{}{"dailyRate": dailyRate.String(), "interestCharged": interestAmount.String(), "chargeDate": chargeDate.Format("2006-01-02")})

	s.logger.Info("EOD: interest accrued",
		zap.String("facilityId", f.ID.String()),
		zap.String("principal", f.DrawnPrincipal.String()),
		zap.String("rate", f.InterestRate.String()),
		zap.String("interest", interestAmount.String()))

	return interestAmount, nil
}

// updateDPDAndStage recalculates Days Past Due and updates NPL staging.
// IFRS 9 staging: PERFORMING (0 DPD) → STAGE1 (1-30) → STAGE2 (31-60) → STAGE3 (61-90) → STAGE4 (90+)
func (s *EODService) updateDPDAndStage(ctx context.Context, f *model.OverdraftFacility, runDate time.Time) (bool, error) {
	previousStage := f.NPLStage
	previousDPD := f.DPD

	// Calculate DPD based on next billing date or approved date
	var referenceDate time.Time
	if f.NextBillingDate != nil && !f.NextBillingDate.IsZero() {
		referenceDate = *f.NextBillingDate
	} else if f.ApprovedAt != nil {
		// If no billing date set, use 30 days from approval as first due date
		referenceDate = f.ApprovedAt.Add(30 * 24 * time.Hour)
	} else {
		referenceDate = f.AppliedAt.Add(30 * 24 * time.Hour)
	}

	// DPD = days past the due date (only if drawn and past due)
	if runDate.After(referenceDate) && f.DrawnAmount.GreaterThan(decimal.Zero) {
		f.DPD = int(runDate.Sub(referenceDate).Hours() / 24)
	} else {
		f.DPD = 0
	}

	// Determine NPL stage based on DPD buckets (IFRS 9)
	switch {
	case f.DPD == 0:
		f.NPLStage = "PERFORMING"
	case f.DPD <= 30:
		f.NPLStage = "STAGE1"
	case f.DPD <= 60:
		f.NPLStage = "STAGE2"
	case f.DPD <= 90:
		f.NPLStage = "STAGE3"
	default:
		f.NPLStage = "STAGE4"
	}

	now := runDate
	f.LastDPDRefresh = &now

	stageChanged := previousStage != f.NPLStage

	// Only update if something changed
	if f.DPD != previousDPD || stageChanged {
		if err := s.repo.UpdateFacility(ctx, f); err != nil {
			return false, fmt.Errorf("update facility DPD: %w", err)
		}

		// Publish DPD updated event
		wallet, _ := s.repo.FindWalletByID(ctx, f.WalletID)
		customerID := ""
		if wallet != nil {
			customerID = wallet.CustomerID
		}
		s.publisher.PublishDpdUpdated(ctx, f.WalletID, customerID, f.DPD, f.NPLStage, f.TenantID)

		// Publish stage change event if stage transitioned
		if stageChanged {
			s.publisher.PublishStageChanged(ctx, f.WalletID, customerID, previousStage, f.NPLStage, f.DPD, f.TenantID)

			s.audit.Audit(ctx, f.TenantID, "FACILITY", f.ID, "STAGE_CHANGED",
				map[string]interface{}{"nplStage": previousStage, "dpd": previousDPD},
				map[string]interface{}{"nplStage": f.NPLStage, "dpd": f.DPD},
				map[string]interface{}{"runDate": runDate.Format("2006-01-02")})

			s.logger.Info("EOD: NPL stage changed",
				zap.String("facilityId", f.ID.String()),
				zap.String("from", previousStage),
				zap.String("to", f.NPLStage),
				zap.Int("dpd", f.DPD))
		}
	}

	return stageChanged, nil
}

// generateBillingIfDue creates a monthly billing statement if the billing date has arrived.
func (s *EODService) generateBillingIfDue(ctx context.Context, f *model.OverdraftFacility, runDate time.Time) (bool, error) {
	// Determine if billing is due today
	if f.NextBillingDate == nil || runDate.Before(*f.NextBillingDate) {
		return false, nil
	}

	billingDate := *f.NextBillingDate

	// Idempotency: check if statement already exists for this billing date
	exists, err := s.repo.BillingStatementExists(ctx, f.ID, billingDate)
	if err != nil {
		return false, fmt.Errorf("check billing existence: %w", err)
	}
	if exists {
		return false, nil
	}

	// Calculate period boundaries
	periodEnd := billingDate
	periodStart := billingDate.AddDate(0, -1, 0)
	if f.LastBillingDate != nil {
		periodStart = *f.LastBillingDate
	}

	// Sum interest charges for the period
	interestAccrued := decimal.Zero
	charges, err := s.repo.ListInterestCharges(ctx, f.ID)
	if err != nil {
		return false, fmt.Errorf("list interest charges: %w", err)
	}
	for _, c := range charges {
		if !c.ChargeDate.Before(periodStart) && c.ChargeDate.Before(periodEnd.AddDate(0, 0, 1)) {
			interestAccrued = interestAccrued.Add(c.InterestCharged)
		}
	}

	// Sum fees for the period
	feesCharged, err := s.repo.SumChargedFeesByFacility(ctx, f.ID)
	if err != nil {
		return false, fmt.Errorf("sum fees: %w", err)
	}

	// Calculate minimum payment due (5% of closing balance or full balance if < 500)
	closingBalance := f.DrawnAmount
	minimumPayment := closingBalance.Mul(decimal.NewFromFloat(0.05)).Round(2)
	threshold := decimal.NewFromInt(500)
	if closingBalance.LessThan(threshold) {
		minimumPayment = closingBalance
	}

	// Due date = 21 days from billing date (standard credit card practice)
	dueDate := billingDate.AddDate(0, 0, 21)

	stmt := &model.OverdraftBillingStatement{
		TenantID:          f.TenantID,
		FacilityID:        f.ID,
		BillingDate:       billingDate,
		PeriodStart:       periodStart,
		PeriodEnd:         periodEnd,
		OpeningBalance:    f.DrawnAmount.Sub(interestAccrued), // approximate opening
		InterestAccrued:   interestAccrued,
		FeesCharged:       feesCharged,
		PaymentsReceived:  decimal.Zero,
		ClosingBalance:    closingBalance,
		MinimumPaymentDue: minimumPayment,
		DueDate:           dueDate,
		Status:            "OPEN",
	}

	if err := s.repo.CreateBillingStatement(ctx, stmt); err != nil {
		return false, fmt.Errorf("create billing statement: %w", err)
	}

	// Update facility billing dates
	nextBilling := billingDate.AddDate(0, 1, 0)
	f.LastBillingDate = &billingDate
	f.NextBillingDate = &nextBilling
	if err := s.repo.UpdateFacility(ctx, f); err != nil {
		return false, fmt.Errorf("update billing dates: %w", err)
	}

	// Publish billing event
	wallet, _ := s.repo.FindWalletByID(ctx, f.WalletID)
	customerID := ""
	if wallet != nil {
		customerID = wallet.CustomerID
	}
	s.publisher.PublishBillingStatement(ctx, f.WalletID, customerID, closingBalance, minimumPayment, dueDate, f.TenantID)

	s.audit.Audit(ctx, f.TenantID, "BILLING", f.ID, "STATEMENT_GENERATED",
		nil,
		map[string]interface{}{
			"billingDate":   billingDate.Format("2006-01-02"),
			"closingBalance": closingBalance.String(),
			"minimumPayment": minimumPayment.String(),
			"dueDate":       dueDate.Format("2006-01-02"),
		}, nil)

	s.logger.Info("EOD: billing statement generated",
		zap.String("facilityId", f.ID.String()),
		zap.String("closingBalance", closingBalance.String()),
		zap.String("minimumPayment", minimumPayment.String()))

	return true, nil
}

// StartScheduler starts the daily EOD scheduler. Runs at the specified hour (UTC).
// Blocks until ctx is cancelled.
func (s *EODService) StartScheduler(ctx context.Context, runHourUTC int) {
	s.logger.Info("EOD scheduler started", zap.Int("runHourUTC", runHourUTC))

	for {
		now := time.Now().UTC()
		// Calculate next run time
		next := time.Date(now.Year(), now.Month(), now.Day(), runHourUTC, 0, 0, 0, time.UTC)
		if now.After(next) {
			next = next.AddDate(0, 0, 1)
		}
		waitDuration := next.Sub(now)

		s.logger.Info("EOD scheduler: next run scheduled",
			zap.Time("nextRun", next),
			zap.Duration("waitDuration", waitDuration))

		select {
		case <-ctx.Done():
			s.logger.Info("EOD scheduler stopped")
			return
		case <-time.After(waitDuration):
			runDate := time.Now().UTC()
			result, err := s.RunEOD(ctx, runDate)
			if err != nil {
				s.logger.Error("EOD scheduler: batch failed", zap.Error(err))
			} else {
				s.logger.Info("EOD scheduler: batch completed",
					zap.Int("facilities", result.FacilitiesProcessed),
					zap.Int("interestCharges", result.InterestChargesCreated))
			}
		}
	}
}

// RunEODForFacility runs interest accrual for a single facility (for API/testing use).
func (s *EODService) RunEODForFacility(ctx context.Context, facilityID uuid.UUID, runDate time.Time) (*EODResult, error) {
	chargeDate := time.Date(runDate.Year(), runDate.Month(), runDate.Day(), 0, 0, 0, 0, time.UTC)
	result := &EODResult{RunDate: chargeDate, FacilitiesProcessed: 1}
	start := time.Now()

	f, err := s.repo.FindFacilityByID(ctx, facilityID)
	if err != nil || f == nil {
		return nil, fmt.Errorf("facility not found: %s", facilityID)
	}

	daysInYear := decimal.NewFromInt(365)

	interest, err := s.accrueInterest(ctx, f, chargeDate, daysInYear)
	if err != nil {
		result.Errors++
		return result, err
	}
	if interest.GreaterThan(decimal.Zero) {
		result.InterestChargesCreated = 1
		result.TotalInterestAccrued = interest.StringFixed(4)
	}

	stageChanged, err := s.updateDPDAndStage(ctx, f, chargeDate)
	if err != nil {
		result.Errors++
	} else {
		result.DPDUpdates = 1
		if stageChanged {
			result.StageChanges = 1
		}
	}

	generated, err := s.generateBillingIfDue(ctx, f, chargeDate)
	if err != nil {
		result.Errors++
	} else if generated {
		result.BillingStatements = 1
	}

	result.DurationMs = time.Since(start).Milliseconds()
	return result, nil
}
