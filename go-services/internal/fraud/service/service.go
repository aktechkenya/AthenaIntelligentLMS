package service

import (
	"context"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/dto"
	"github.com/athena-lms/go-services/internal/fraud/model"
	"github.com/athena-lms/go-services/internal/fraud/repository"
)

// Service contains the business logic for fraud detection.
type Service struct {
	repo   *repository.Repository
	logger *zap.Logger
}

// New creates a new fraud detection Service.
func New(repo *repository.Repository, logger *zap.Logger) *Service {
	return &Service{repo: repo, logger: logger}
}

// GetSummary returns an aggregate fraud summary for the tenant.
func (s *Service) GetSummary(ctx context.Context, tenantID string) (*model.FraudSummaryResponse, error) {
	open, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.StatusOpen)
	if err != nil {
		return nil, err
	}
	underReview, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.StatusUnderReview)
	if err != nil {
		return nil, err
	}
	escalated, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.StatusEscalated)
	if err != nil {
		return nil, err
	}
	confirmed, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.StatusConfirmedFraud)
	if err != nil {
		return nil, err
	}
	critical, err := s.repo.CountAlertsBySeverityAndStatus(ctx, tenantID, model.SeverityCritical, model.StatusOpen)
	if err != nil {
		return nil, err
	}
	highRisk, err := s.repo.CountRiskProfilesByLevel(ctx, tenantID, model.RiskHigh)
	if err != nil {
		return nil, err
	}
	criticalRisk, err := s.repo.CountRiskProfilesByLevel(ctx, tenantID, model.RiskCritical)
	if err != nil {
		return nil, err
	}

	return &model.FraudSummaryResponse{
		TenantID:              tenantID,
		OpenAlerts:            open,
		UnderReviewAlerts:     underReview,
		EscalatedAlerts:       escalated,
		ConfirmedFraud:        confirmed,
		CriticalAlerts:        critical,
		HighRiskCustomers:     highRisk,
		CriticalRiskCustomers: criticalRisk,
	}, nil
}

// ListAlerts returns paginated alerts for a tenant.
func (s *Service) ListAlerts(ctx context.Context, tenantID string, status *model.AlertStatus, page, size int) ([]*model.FraudAlert, int64, error) {
	return s.repo.ListAlerts(ctx, tenantID, status, page, size)
}

// GetAlert returns a single alert by ID.
func (s *Service) GetAlert(ctx context.Context, id uuid.UUID) (*model.FraudAlert, error) {
	return s.repo.GetAlert(ctx, id)
}

// ResolveAlert resolves an alert.
func (s *Service) ResolveAlert(ctx context.Context, id uuid.UUID, req model.ResolveAlertRequest, tenantID string) (*model.FraudAlert, error) {
	alert, err := s.repo.GetAlert(ctx, id)
	if err != nil {
		return nil, err
	}
	if alert == nil {
		return nil, nil
	}

	now := time.Now()
	alert.ResolvedBy = &req.ResolvedBy
	alert.ResolvedAt = &now
	alert.ResolutionNotes = &req.Notes

	if req.ConfirmedFraud != nil && *req.ConfirmedFraud {
		alert.Status = model.StatusConfirmedFraud
		resolution := "CONFIRMED_FRAUD"
		alert.Resolution = &resolution
	} else {
		alert.Status = model.StatusFalsePositive
		resolution := "FALSE_POSITIVE"
		alert.Resolution = &resolution
	}
	alert.UpdatedAt = now

	if err := s.repo.UpdateAlert(ctx, alert); err != nil {
		return nil, err
	}

	return alert, nil
}

// AssignAlert assigns an alert to a user.
func (s *Service) AssignAlert(ctx context.Context, id uuid.UUID, req model.AssignAlertRequest) (*model.FraudAlert, error) {
	alert, err := s.repo.GetAlert(ctx, id)
	if err != nil {
		return nil, err
	}
	if alert == nil {
		return nil, nil
	}

	alert.AssignedTo = &req.Assignee
	alert.Status = model.StatusUnderReview
	alert.UpdatedAt = time.Now()

	if err := s.repo.UpdateAlert(ctx, alert); err != nil {
		return nil, err
	}

	return alert, nil
}

// ListRules returns all fraud rules for a tenant.
func (s *Service) ListRules(ctx context.Context, tenantID string) ([]*model.FraudRule, error) {
	return s.repo.FindAllRules(ctx, tenantID)
}

// GetRule returns a single rule by ID.
func (s *Service) GetRule(ctx context.Context, id uuid.UUID) (*model.FraudRule, error) {
	return s.repo.GetRule(ctx, id)
}

// UpdateRule updates a fraud rule.
func (s *Service) UpdateRule(ctx context.Context, id uuid.UUID, req model.UpdateRuleRequest) (*model.FraudRule, error) {
	rule, err := s.repo.GetRule(ctx, id)
	if err != nil {
		return nil, err
	}
	if rule == nil {
		return nil, nil
	}

	if req.Enabled != nil {
		rule.Enabled = *req.Enabled
	}
	if req.Severity != nil {
		rule.Severity = model.AlertSeverity(*req.Severity)
	}
	if req.Parameters != nil {
		rule.Parameters = req.Parameters
	}
	rule.UpdatedAt = time.Now()

	if err := s.repo.UpdateRule(ctx, rule); err != nil {
		return nil, err
	}

	return rule, nil
}

// ListCases returns paginated fraud cases.
func (s *Service) ListCases(ctx context.Context, tenantID string, status *model.CaseStatus, page, size int) ([]*model.FraudCase, int64, error) {
	return s.repo.ListCases(ctx, tenantID, status, page, size)
}

// GetCase returns a single case by ID.
func (s *Service) GetCase(ctx context.Context, id uuid.UUID) (*model.FraudCase, error) {
	return s.repo.GetCase(ctx, id)
}

// ListCaseNotes returns notes for a case.
func (s *Service) ListCaseNotes(ctx context.Context, caseID uuid.UUID, tenantID string) ([]*model.CaseNote, error) {
	return s.repo.ListCaseNotes(ctx, caseID, tenantID)
}

// AddCaseNote adds a note to a case.
func (s *Service) AddCaseNote(ctx context.Context, caseID uuid.UUID, req model.AddCaseNoteRequest, tenantID string) (*model.CaseNote, error) {
	noteType := req.NoteType
	if noteType == "" {
		noteType = "COMMENT"
	}

	note := &model.CaseNote{
		CaseID:   caseID,
		TenantID: tenantID,
		Author:   req.Author,
		Content:  req.Content,
		NoteType: noteType,
	}

	if err := s.repo.CreateCaseNote(ctx, note); err != nil {
		return nil, err
	}

	return note, nil
}

// ListWatchlistEntries returns paginated watchlist entries.
func (s *Service) ListWatchlistEntries(ctx context.Context, tenantID string, active *bool, page, size int) ([]*model.WatchlistEntry, int64, error) {
	return s.repo.ListWatchlistEntries(ctx, tenantID, active, page, size)
}

// GetWatchlistEntry returns a single watchlist entry by ID.
func (s *Service) GetWatchlistEntry(ctx context.Context, id uuid.UUID) (*model.WatchlistEntry, error) {
	return s.repo.GetWatchlistEntry(ctx, id)
}

// CreateWatchlistEntry creates a new watchlist entry.
func (s *Service) CreateWatchlistEntry(ctx context.Context, req model.CreateWatchlistEntryRequest, tenantID string) (*model.WatchlistEntry, error) {
	entry := &model.WatchlistEntry{
		TenantID:  tenantID,
		ListType:  model.WatchlistType(req.ListType),
		EntryType: req.EntryType,
		Name:      strPtr(req.Name),
		NationalID: strPtr(req.NationalID),
		Phone:     strPtr(req.Phone),
		Reason:    strPtr(req.Reason),
		Source:    strPtr(req.Source),
		Active:    true,
		ExpiresAt: req.ExpiresAt,
	}

	if err := s.repo.CreateWatchlistEntry(ctx, entry); err != nil {
		return nil, err
	}

	return entry, nil
}

// ListHighRiskCustomers returns paginated high-risk customer profiles.
func (s *Service) ListHighRiskCustomers(ctx context.Context, tenantID string, page, size int) ([]*model.CustomerRiskProfile, int64, error) {
	return s.repo.ListHighRiskCustomers(ctx, tenantID, page, size)
}

// GetRiskProfile returns the risk profile for a customer.
func (s *Service) GetRiskProfile(ctx context.Context, tenantID, customerID string) (*model.CustomerRiskProfile, error) {
	return s.repo.GetRiskProfile(ctx, tenantID, customerID)
}

// GetAnalytics returns fraud analytics data.
func (s *Service) GetAnalytics(ctx context.Context, tenantID string) (*model.FraudAnalyticsResponse, error) {
	totalAlerts, err := s.repo.CountAlerts(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	resolved, err := s.repo.CountResolved(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	confirmed, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.StatusConfirmedFraud)
	if err != nil {
		return nil, err
	}
	falsePos, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.StatusFalsePositive)
	if err != nil {
		return nil, err
	}

	activeCases, err := s.repo.CountActiveCases(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	var resolutionRate float64
	if totalAlerts > 0 {
		resolutionRate = float64(resolved) / float64(totalAlerts) * 100
	}

	var precisionRate float64
	totalResolved := confirmed + falsePos
	if totalResolved > 0 {
		precisionRate = float64(confirmed) / float64(totalResolved) * 100
	}

	// Rule effectiveness
	byRule, _ := s.repo.CountByRule(ctx, tenantID)
	confirmedByRule, _ := s.repo.CountConfirmedByRule(ctx, tenantID)
	falsePosByRule, _ := s.repo.CountFalsePositiveByRule(ctx, tenantID)

	confirmedMap := make(map[string]int64)
	for _, c := range confirmedByRule {
		confirmedMap[c.RuleCode] = c.Count
	}
	falsePosMap := make(map[string]int64)
	for _, f := range falsePosByRule {
		falsePosMap[f.RuleCode] = f.Count
	}

	ruleEffectiveness := make([]model.RuleEffectiveness, 0, len(byRule))
	for _, r := range byRule {
		c := confirmedMap[r.RuleCode]
		f := falsePosMap[r.RuleCode]
		var pr float64
		if c+f > 0 {
			pr = float64(c) / float64(c+f) * 100
		}
		ruleEffectiveness = append(ruleEffectiveness, model.RuleEffectiveness{
			RuleCode:       r.RuleCode,
			TotalTriggers:  r.Count,
			ConfirmedFraud: c,
			FalsePositives: f,
			PrecisionRate:  pr,
		})
	}

	since := time.Now().AddDate(0, 0, -30)
	dailyTrend, _ := s.repo.CountByDay(ctx, tenantID, since)
	if dailyTrend == nil {
		dailyTrend = []model.DailyAlertCount{}
	}

	alertsByType, _ := s.repo.CountByAlertType(ctx, tenantID, since)
	if alertsByType == nil {
		alertsByType = []model.TypeCount{}
	}

	return &model.FraudAnalyticsResponse{
		TotalAlerts:         totalAlerts,
		ResolvedAlerts:      resolved,
		ResolutionRate:      resolutionRate,
		ActiveCases:         activeCases,
		ConfirmedFraudCount: confirmed,
		FalsePositiveCount:  falsePos,
		PrecisionRate:       precisionRate,
		RuleEffectiveness:   ruleEffectiveness,
		DailyTrend:          dailyTrend,
		AlertsByType:        alertsByType,
	}, nil
}

// ListAuditLog returns paginated audit log entries.
func (s *Service) ListAuditLog(ctx context.Context, tenantID string, entityType *string, entityID *uuid.UUID, page, size int) (dto.PageResponse, error) {
	logs, total, err := s.repo.ListAuditLog(ctx, tenantID, entityType, entityID, page, size)
	if err != nil {
		return dto.PageResponse{}, err
	}
	return dto.NewPageResponse(logs, page, size, total), nil
}

// ListNetworkLinks returns network links for a customer.
func (s *Service) ListNetworkLinks(ctx context.Context, tenantID, customerID string) ([]*model.NetworkLink, error) {
	return s.repo.FindLinksByCustomer(ctx, tenantID, customerID)
}

// ---- helpers ----

func strPtr(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
