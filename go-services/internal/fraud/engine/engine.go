package engine

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/fraud/model"
	"github.com/athena-lms/go-services/internal/fraud/repository"
)

// Engine evaluates transaction events against enabled fraud rules and creates
// alerts for any triggered rules.
type Engine struct {
	repo   *repository.Repository
	logger *zap.Logger
}

// New creates a new rule evaluation Engine.
func New(repo *repository.Repository, logger *zap.Logger) *Engine {
	return &Engine{repo: repo, logger: logger}
}

// Evaluate runs a transaction event through all matching enabled rules and
// returns the evaluation result including any triggered rules and created alerts.
func (e *Engine) Evaluate(ctx context.Context, tenantID string, req model.EvaluateTransactionRequest) (*model.EvaluateTransactionResponse, error) {
	// 1. Load all enabled rules for the tenant.
	rules, err := e.repo.FindActiveRules(ctx, tenantID)
	if err != nil {
		return nil, fmt.Errorf("loading active rules: %w", err)
	}

	// 2. Filter to rules whose event_types include the request's EventType.
	var matching []*model.FraudRule
	for _, r := range rules {
		if r.AppliesTo(req.EventType) {
			matching = append(matching, r)
		}
	}

	eventID := uuid.New()
	now := time.Now()
	var triggered []model.TriggeredRule
	var alertsCreated int

	// 3. Evaluate each matching rule.
	for _, rule := range matching {
		ok, desc := e.evaluateRule(ctx, tenantID, rule, req, now)
		if !ok {
			continue
		}

		triggered = append(triggered, model.TriggeredRule{
			RuleCode:    rule.RuleCode,
			RuleName:    rule.RuleName,
			Severity:    string(rule.Severity),
			Description: desc,
		})

		// 4. Create a FraudAlert for each triggered rule.
		alertType := ruleCodeToAlertType(rule.RuleCode)
		ruleCode := rule.RuleCode
		triggerEvent := req.EventType
		alert := &model.FraudAlert{
			TenantID:     tenantID,
			AlertType:    alertType,
			Severity:     rule.Severity,
			Status:       model.StatusOpen,
			Source:       model.SourceRuleEngine,
			RuleCode:     &ruleCode,
			CustomerID:   &req.CustomerID,
			SubjectType:  req.SubjectType,
			SubjectID:    req.SubjectID,
			Description:  desc,
			TriggerEvent: &triggerEvent,
			TriggerAmount: req.Amount,
		}
		if err := e.repo.CreateAlert(ctx, alert); err != nil {
			e.logger.Error("failed to create alert", zap.String("ruleCode", rule.RuleCode), zap.Error(err))
			continue
		}
		alertsCreated++
	}

	// 5. Update velocity counter for this event type.
	if req.Amount != nil {
		windowStart := now.Truncate(time.Hour)
		counter := &model.VelocityCounter{
			TenantID:    tenantID,
			CustomerID:  req.CustomerID,
			CounterType: req.EventType,
			WindowStart: windowStart,
			WindowEnd:   windowStart.Add(time.Hour),
			Count:       1,
			TotalAmount: *req.Amount,
		}
		// Try to load existing counter for this window to increment.
		existing, err := e.repo.FindVelocityCounter(ctx, tenantID, req.CustomerID, req.EventType, windowStart)
		if err == nil && existing != nil {
			counter.ID = existing.ID
			counter.Count = existing.Count + 1
			counter.TotalAmount = existing.TotalAmount.Add(*req.Amount)
			counter.CreatedAt = existing.CreatedAt
		}
		if err := e.repo.UpsertVelocityCounter(ctx, counter); err != nil {
			e.logger.Error("failed to upsert velocity counter", zap.Error(err))
		}
	}

	// 6. Record the fraud event.
	riskScore := computeRiskScore(triggered)
	riskScoreDec := decimal.NewFromFloat(riskScore)
	var rulesTriggeredStr *string
	if len(triggered) > 0 {
		codes := make([]string, len(triggered))
		for i, t := range triggered {
			codes[i] = t.RuleCode
		}
		joined := strings.Join(codes, ",")
		rulesTriggeredStr = &joined
	}

	fraudEvent := &model.FraudEvent{
		TenantID:       tenantID,
		EventType:      req.EventType,
		CustomerID:     &req.CustomerID,
		SubjectID:      &req.SubjectID,
		Amount:         req.Amount,
		RiskScore:      &riskScoreDec,
		RulesTriggered: rulesTriggeredStr,
		ProcessedAt:    now,
	}
	// Marshal the request as payload.
	if payload, err := json.Marshal(req); err == nil {
		fraudEvent.Payload = payload
	}
	if err := e.repo.CreateEvent(ctx, fraudEvent); err != nil {
		e.logger.Error("failed to create fraud event", zap.Error(err))
	}

	// 7. Update customer risk profile.
	e.updateRiskProfile(ctx, tenantID, req.CustomerID, riskScore, len(triggered), now)

	return &model.EvaluateTransactionResponse{
		EventID:        eventID,
		CustomerID:     req.CustomerID,
		RulesEvaluated: len(matching),
		RulesTriggered: len(triggered),
		TriggeredRules: triggered,
		AlertsCreated:  alertsCreated,
		RiskScore:      riskScore,
	}, nil
}

// evaluateRule checks whether a single rule is triggered by the request.
// Returns (triggered, description).
func (e *Engine) evaluateRule(ctx context.Context, tenantID string, rule *model.FraudRule, req model.EvaluateTransactionRequest, now time.Time) (bool, string) {
	p := rule.Parameters
	if p == nil {
		p = map[string]interface{}{}
	}

	switch rule.RuleCode {

	case "LARGE_SINGLE_TXN":
		threshold := decimal.NewFromFloat(paramFloat(p, "threshold", 1000000))
		if req.Amount != nil && req.Amount.GreaterThan(threshold) {
			return true, fmt.Sprintf("Transaction amount %s exceeds threshold %s", req.Amount.StringFixed(2), threshold.StringFixed(2))
		}

	case "STRUCTURING":
		threshold := decimal.NewFromFloat(paramFloat(p, "threshold", 1000000))
		windowHours := paramFloat(p, "windowHours", 24)
		minTxns := int(paramFloat(p, "minTransactions", 3))
		perTxnCeiling := decimal.NewFromFloat(paramFloat(p, "perTxnCeiling", 999999))
		since := now.Add(-time.Duration(windowHours) * time.Hour)

		// Current transaction must be under the per-txn ceiling (structuring pattern).
		if req.Amount == nil || req.Amount.GreaterThan(perTxnCeiling) {
			return false, ""
		}

		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			e.logger.Warn("structuring: count query failed", zap.Error(err))
			return false, ""
		}
		totalCount := count + 1 // include current
		if totalCount < minTxns {
			return false, ""
		}

		amount, err := e.repo.SumAmountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			e.logger.Warn("structuring: amount query failed", zap.Error(err))
			return false, ""
		}
		totalAmount := amount
		if req.Amount != nil {
			totalAmount = totalAmount.Add(*req.Amount)
		}
		if totalAmount.GreaterThanOrEqual(threshold) {
			return true, fmt.Sprintf("Structuring detected: %d transactions totalling %s in %dh window (threshold %s)", totalCount, totalAmount.StringFixed(2), int(windowHours), threshold.StringFixed(2))
		}

	case "HIGH_VELOCITY_1H":
		maxTxns := int(paramFloat(p, "maxTransactions", 10))
		windowMin := int(paramFloat(p, "windowMinutes", 60))
		since := now.Add(-time.Duration(windowMin) * time.Minute)
		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			return false, ""
		}
		if count+1 > maxTxns {
			return true, fmt.Sprintf("High velocity: %d transactions in %d minutes (max %d)", count+1, windowMin, maxTxns)
		}

	case "HIGH_VELOCITY_24H":
		maxTxns := int(paramFloat(p, "maxTransactions", 50))
		windowMin := int(paramFloat(p, "windowMinutes", 1440))
		since := now.Add(-time.Duration(windowMin) * time.Minute)
		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			return false, ""
		}
		if count+1 > maxTxns {
			return true, fmt.Sprintf("High velocity: %d transactions in %d hours (max %d)", count+1, windowMin/60, maxTxns)
		}

	case "ROUND_AMOUNT_PATTERN":
		roundThreshold := int64(paramFloat(p, "roundThreshold", 10000))
		if req.Amount == nil || roundThreshold == 0 {
			return false, ""
		}
		amt := req.Amount.IntPart()
		if amt > 0 && amt%roundThreshold == 0 {
			// Check count of round transactions in window.
			minRound := int(paramFloat(p, "minRoundTxns", 5))
			windowHours := paramFloat(p, "windowHours", 24)
			since := now.Add(-time.Duration(windowHours) * time.Hour)
			counterType := "round_amount." + req.EventType
			count, _ := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, counterType, since)
			if count+1 >= minRound {
				return true, fmt.Sprintf("Round amount pattern: %d round transactions of %s+ in %dh", count+1, decimal.NewFromInt(roundThreshold).StringFixed(0), int(windowHours))
			}
			// Track round amount in a separate counter so future checks accumulate.
			e.upsertRoundCounter(ctx, tenantID, req.CustomerID, counterType, req.Amount, now)
		}

	case "RAPID_FUND_MOVEMENT":
		windowMin := int(paramFloat(p, "windowMinutes", 15))
		since := now.Add(-time.Duration(windowMin) * time.Minute)
		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			return false, ""
		}
		if count+1 > 1 {
			return true, fmt.Sprintf("Rapid fund movement: %d transfers in %d minutes", count+1, windowMin)
		}

	case "APPLICATION_STACKING":
		maxApps := int(paramFloat(p, "maxApplications", 5))
		windowDays := int(paramFloat(p, "windowDays", 30))
		since := now.AddDate(0, 0, -windowDays)
		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			return false, ""
		}
		if count+1 > maxApps {
			return true, fmt.Sprintf("Application stacking: %d applications in %d days (max %d)", count+1, windowDays, maxApps)
		}

	case "EARLY_PAYOFF_SUSPICIOUS":
		minDays := int(paramFloat(p, "minDaysForAlert", 30))
		if req.LoanDisbursedAt == nil {
			return false, ""
		}
		daysSinceDisbursement := int(now.Sub(*req.LoanDisbursedAt).Hours() / 24)
		if daysSinceDisbursement < minDays {
			return true, fmt.Sprintf("Early payoff: loan closed %d days after disbursement (threshold %d days)", daysSinceDisbursement, minDays)
		}

	case "LOAN_CYCLING":
		windowDays := int(paramFloat(p, "windowDays", 7))
		since := now.AddDate(0, 0, -windowDays)
		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			return false, ""
		}
		if count+1 > 1 {
			return true, fmt.Sprintf("Loan cycling: %d loan applications in %d days", count+1, windowDays)
		}

	case "DORMANT_REACTIVATION":
		dormantDays := int(paramFloat(p, "dormantDays", 180))
		if req.AccountLastActiveAt == nil {
			return false, ""
		}
		daysSinceActive := int(now.Sub(*req.AccountLastActiveAt).Hours() / 24)
		if daysSinceActive >= dormantDays {
			return true, fmt.Sprintf("Dormant account reactivation: inactive for %d days (threshold %d)", daysSinceActive, dormantDays)
		}

	case "KYC_BYPASS_ATTEMPT":
		if req.KycStatus == "" || req.KycStatus == "PASSED" || req.KycStatus == "APPROVED" {
			return false, ""
		}
		return true, fmt.Sprintf("Transaction attempted with KYC status: %s", req.KycStatus)

	case "OVERDRAFT_RAPID_DRAW":
		thresholdPct := paramFloat(p, "drawdownThresholdPercent", 90)
		if req.Amount == nil || req.OverdraftLimit == nil || req.OverdraftLimit.IsZero() {
			return false, ""
		}
		pct := req.Amount.Div(*req.OverdraftLimit).Mul(decimal.NewFromInt(100))
		if pct.GreaterThanOrEqual(decimal.NewFromFloat(thresholdPct)) {
			return true, fmt.Sprintf("Overdraft rapid draw: %s%% of limit drawn (threshold %d%%)", pct.StringFixed(1), int(thresholdPct))
		}

	case "BNPL_ABUSE":
		maxApprovals := int(paramFloat(p, "maxApprovals", 3))
		windowDays := int(paramFloat(p, "windowDays", 7))
		since := now.AddDate(0, 0, -windowDays)
		count, err := e.repo.SumCountSince(ctx, tenantID, req.CustomerID, req.EventType, since)
		if err != nil {
			return false, ""
		}
		if count+1 > maxApprovals {
			return true, fmt.Sprintf("BNPL abuse: %d approvals in %d days (max %d)", count+1, windowDays, maxApprovals)
		}

	case "PAYMENT_REVERSAL_ABUSE":
		maxReversalPct := paramFloat(p, "maxReversalPercent", 30)
		minPayments := int(paramFloat(p, "minPayments", 5))
		if req.TotalPayments < minPayments {
			return false, ""
		}
		pct := float64(req.ReversalCount) / float64(req.TotalPayments) * 100
		if pct >= maxReversalPct {
			return true, fmt.Sprintf("Payment reversal abuse: %.1f%% reversal rate (%d/%d, threshold %.0f%%)", pct, req.ReversalCount, req.TotalPayments, maxReversalPct)
		}

	case "OVERPAYMENT":
		thresholdPct := paramFloat(p, "overpaymentThresholdPercent", 110)
		if req.Amount == nil || req.LoanOutstanding == nil || req.LoanOutstanding.IsZero() {
			return false, ""
		}
		pct := req.Amount.Div(*req.LoanOutstanding).Mul(decimal.NewFromInt(100))
		if pct.GreaterThanOrEqual(decimal.NewFromFloat(thresholdPct)) {
			return true, fmt.Sprintf("Overpayment: payment is %s%% of outstanding balance (threshold %d%%)", pct.StringFixed(1), int(thresholdPct))
		}

	case "SUSPICIOUS_WRITEOFF":
		recentDays := int(paramFloat(p, "recentPaymentDays", 30))
		if req.LoanDisbursedAt == nil {
			return false, ""
		}
		daysSinceDisbursement := int(now.Sub(*req.LoanDisbursedAt).Hours() / 24)
		if daysSinceDisbursement < recentDays {
			return true, fmt.Sprintf("Suspicious write-off: loan written off only %d days after disbursement (threshold %d)", daysSinceDisbursement, recentDays)
		}

	case "WATCHLIST_MATCH":
		matches, err := e.repo.FindWatchlistMatches(ctx, tenantID, req.NationalID, req.Name, req.Phone)
		if err != nil {
			e.logger.Warn("watchlist match query failed", zap.Error(err))
			return false, ""
		}
		if len(matches) > 0 {
			listTypes := make([]string, 0, len(matches))
			for _, m := range matches {
				listTypes = append(listTypes, string(m.ListType))
			}
			return true, fmt.Sprintf("Watchlist match: %d hit(s) on lists: %s", len(matches), strings.Join(listTypes, ", "))
		}

	default:
		e.logger.Debug("unknown rule code, skipping", zap.String("ruleCode", rule.RuleCode))
	}

	return false, ""
}

// updateRiskProfile loads or creates the customer risk profile and updates it.
func (e *Engine) updateRiskProfile(ctx context.Context, tenantID, customerID string, riskScore float64, newAlerts int, now time.Time) {
	profile, err := e.repo.GetRiskProfile(ctx, tenantID, customerID)
	if err != nil {
		// Profile doesn't exist yet; create one.
		profile = &model.CustomerRiskProfile{
			TenantID:   tenantID,
			CustomerID: customerID,
			RiskScore:  decimal.NewFromFloat(riskScore),
			RiskLevel:  riskLevelFromScore(riskScore),
		}
	}

	profile.TotalAlerts += newAlerts
	profile.OpenAlerts += newAlerts
	profile.TransactionCount30d++
	profile.LastAlertAt = &now
	profile.LastScoredAt = &now

	// Blend the new risk score with existing (exponential moving average).
	oldScore, _ := profile.RiskScore.Float64()
	blended := oldScore*0.7 + riskScore*0.3
	if riskScore > oldScore {
		// Bias upward on new high-risk events.
		blended = oldScore*0.4 + riskScore*0.6
	}
	profile.RiskScore = decimal.NewFromFloat(blended)
	profile.RiskLevel = riskLevelFromScore(blended)

	if err := e.repo.UpsertRiskProfile(ctx, profile); err != nil {
		e.logger.Error("failed to upsert risk profile", zap.Error(err))
	}
}

// upsertRoundCounter tracks round-amount transactions in a dedicated counter.
func (e *Engine) upsertRoundCounter(ctx context.Context, tenantID, customerID, counterType string, amount *decimal.Decimal, now time.Time) {
	windowStart := now.Truncate(time.Hour)
	existing, err := e.repo.FindVelocityCounter(ctx, tenantID, customerID, counterType, windowStart)
	counter := &model.VelocityCounter{
		TenantID:    tenantID,
		CustomerID:  customerID,
		CounterType: counterType,
		WindowStart: windowStart,
		WindowEnd:   windowStart.Add(time.Hour),
		Count:       1,
		TotalAmount: decimal.Zero,
	}
	if amount != nil {
		counter.TotalAmount = *amount
	}
	if err == nil && existing != nil {
		counter.ID = existing.ID
		counter.Count = existing.Count + 1
		counter.TotalAmount = existing.TotalAmount.Add(counter.TotalAmount)
		counter.CreatedAt = existing.CreatedAt
	}
	if err := e.repo.UpsertVelocityCounter(ctx, counter); err != nil {
		e.logger.Error("failed to upsert round counter", zap.Error(err))
	}
}

// computeRiskScore returns a 0.0-1.0 score based on triggered rules.
func computeRiskScore(triggered []model.TriggeredRule) float64 {
	if len(triggered) == 0 {
		return 0.0
	}
	var total float64
	for _, t := range triggered {
		total += severityWeight(t.Severity)
	}
	// Cap at 1.0.
	if total > 1.0 {
		total = 1.0
	}
	return total
}

func severityWeight(sev string) float64 {
	switch sev {
	case "LOW":
		return 0.1
	case "MEDIUM":
		return 0.3
	case "HIGH":
		return 0.5
	case "CRITICAL":
		return 0.8
	default:
		return 0.1
	}
}

func riskLevelFromScore(score float64) model.RiskLevel {
	switch {
	case score >= 0.7:
		return model.RiskCritical
	case score >= 0.4:
		return model.RiskHigh
	case score >= 0.2:
		return model.RiskMedium
	default:
		return model.RiskLow
	}
}

// ruleCodeToAlertType maps a rule code to the corresponding AlertType enum.
func ruleCodeToAlertType(code string) model.AlertType {
	switch code {
	case "LARGE_SINGLE_TXN":
		return model.AlertLargeTransaction
	case "STRUCTURING":
		return model.AlertStructuring
	case "HIGH_VELOCITY_1H", "HIGH_VELOCITY_24H":
		return model.AlertHighVelocity
	case "ROUND_AMOUNT_PATTERN":
		return model.AlertRoundAmountPattern
	case "RAPID_FUND_MOVEMENT":
		return model.AlertRapidFundMovement
	case "APPLICATION_STACKING":
		return model.AlertApplicationStacking
	case "EARLY_PAYOFF_SUSPICIOUS":
		return model.AlertEarlyPayoff
	case "LOAN_CYCLING":
		return model.AlertLoanCycling
	case "DORMANT_REACTIVATION":
		return model.AlertDormantReactivation
	case "KYC_BYPASS_ATTEMPT":
		return model.AlertKYCBypass
	case "OVERDRAFT_RAPID_DRAW":
		return model.AlertOverdraftAbuse
	case "BNPL_ABUSE":
		return model.AlertBNPLAbuse
	case "PAYMENT_REVERSAL_ABUSE":
		return model.AlertPaymentReversal
	case "OVERPAYMENT":
		return model.AlertOverpayment
	case "SUSPICIOUS_WRITEOFF":
		return model.AlertSuspiciousWriteoff
	case "WATCHLIST_MATCH":
		return model.AlertWatchlistMatch
	default:
		return model.AlertType(code)
	}
}

// paramFloat extracts a float64 from a map[string]interface{} with a fallback.
func paramFloat(params map[string]interface{}, key string, fallback float64) float64 {
	if v, ok := params[key]; ok {
		switch f := v.(type) {
		case float64:
			return f
		case int:
			return float64(f)
		case int64:
			return float64(f)
		case json.Number:
			if n, err := f.Float64(); err == nil {
				return n
			}
		}
	}
	return fallback
}
