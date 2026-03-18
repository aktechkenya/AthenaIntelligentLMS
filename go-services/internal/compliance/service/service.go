package service

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/dto"
	"github.com/athena-lms/go-services/internal/common/errors"
	"github.com/athena-lms/go-services/internal/compliance/event"
	"github.com/athena-lms/go-services/internal/compliance/model"
	"github.com/athena-lms/go-services/internal/compliance/repository"
)

// Service implements compliance business logic.
type Service struct {
	repo      *repository.Repository
	publisher *event.Publisher
	logger    *zap.Logger
}

// New creates a new compliance Service.
func New(repo *repository.Repository, publisher *event.Publisher, logger *zap.Logger) *Service {
	return &Service{repo: repo, publisher: publisher, logger: logger}
}

// ─── AML Alerts ─────────────────────────────────────────────────────────────

// CreateAlert creates a new AML alert and publishes an event.
func (s *Service) CreateAlert(ctx context.Context, req model.CreateAlertRequest, tenantID string) (*model.AmlAlert, error) {
	if !model.ValidAlertType(string(req.AlertType)) {
		return nil, errors.BadRequest("invalid alert type: " + string(req.AlertType))
	}
	if req.SubjectType == "" {
		return nil, errors.BadRequest("subject type is required")
	}
	if req.SubjectID == "" {
		return nil, errors.BadRequest("subject ID is required")
	}
	if req.Description == "" {
		return nil, errors.BadRequest("description is required")
	}

	severity := model.AlertSeverityMedium
	if req.Severity != nil {
		severity = *req.Severity
	}

	alert := &model.AmlAlert{
		TenantID:      tenantID,
		AlertType:     req.AlertType,
		Severity:      severity,
		Status:        model.AlertStatusOpen,
		SubjectType:   req.SubjectType,
		SubjectID:     req.SubjectID,
		CustomerID:    req.CustomerID,
		Description:   req.Description,
		TriggerEvent:  req.TriggerEvent,
		TriggerAmount: req.TriggerAmount,
		SarFiled:      false,
	}

	alert, err := s.repo.CreateAlert(ctx, alert)
	if err != nil {
		return nil, fmt.Errorf("create alert: %w", err)
	}

	s.logger.Info("Created AML alert",
		zap.String("id", alert.ID.String()),
		zap.String("type", string(alert.AlertType)),
		zap.String("tenant", tenantID))

	customerID := ""
	if alert.CustomerID != nil {
		customerID = *alert.CustomerID
	}
	s.publisher.PublishAmlAlertRaised(ctx, alert.ID, string(alert.AlertType), customerID, tenantID)

	return alert, nil
}

// GetAlert retrieves an alert by ID and tenant.
func (s *Service) GetAlert(ctx context.Context, id uuid.UUID, tenantID string) (*model.AmlAlert, error) {
	alert, err := s.repo.GetAlertByID(ctx, id, tenantID)
	if err != nil {
		return nil, err
	}
	if alert == nil {
		return nil, errors.NotFoundResource("AML alert", id)
	}
	return alert, nil
}

// ListAlerts returns a paginated list of alerts.
func (s *Service) ListAlerts(ctx context.Context, tenantID string, status *model.AlertStatus, page, size int) (dto.PageResponse, error) {
	alerts, total, err := s.repo.ListAlerts(ctx, tenantID, status, page, size)
	if err != nil {
		return dto.PageResponse{}, err
	}
	if alerts == nil {
		alerts = []model.AmlAlert{}
	}
	return dto.NewPageResponse(alerts, page, size, total), nil
}

// ResolveAlert resolves an alert.
func (s *Service) ResolveAlert(ctx context.Context, id uuid.UUID, req model.ResolveAlertRequest, tenantID string) (*model.AmlAlert, error) {
	if req.ResolvedBy == "" {
		return nil, errors.BadRequest("resolved by is required")
	}
	if req.ResolutionNotes == "" {
		return nil, errors.BadRequest("resolution notes are required")
	}

	alert, err := s.repo.GetAlertByID(ctx, id, tenantID)
	if err != nil {
		return nil, err
	}
	if alert == nil {
		return nil, errors.NotFoundResource("AML alert", id)
	}

	now := time.Now()
	alert.Status = model.AlertStatusClosedActioned
	alert.ResolvedBy = &req.ResolvedBy
	alert.ResolvedAt = &now
	alert.ResolutionNotes = &req.ResolutionNotes

	if err := s.repo.UpdateAlert(ctx, alert); err != nil {
		return nil, err
	}

	s.logger.Info("Resolved AML alert",
		zap.String("id", id.String()),
		zap.String("resolvedBy", req.ResolvedBy),
		zap.String("tenant", tenantID))

	return alert, nil
}

// FileSar creates a SAR filing for an alert and publishes an event.
func (s *Service) FileSar(ctx context.Context, alertID uuid.UUID, req model.FileSarRequest, tenantID string) (*model.SarFiling, error) {
	if req.ReferenceNumber == "" {
		return nil, errors.BadRequest("reference number is required")
	}

	alert, err := s.repo.GetAlertByID(ctx, alertID, tenantID)
	if err != nil {
		return nil, err
	}
	if alert == nil {
		return nil, errors.NotFoundResource("AML alert", alertID)
	}

	filingDate := time.Now().Format("2006-01-02")
	if req.FilingDate != nil {
		filingDate = *req.FilingDate
	}

	regulator := "FRC Kenya"
	if req.Regulator != nil {
		regulator = *req.Regulator
	}

	sar := &model.SarFiling{
		TenantID:        tenantID,
		AlertID:         alertID,
		ReferenceNumber: req.ReferenceNumber,
		FilingDate:      filingDate,
		Regulator:       regulator,
		Status:          "FILED",
		SubmittedBy:     req.SubmittedBy,
		Notes:           req.Notes,
	}

	sar, err = s.repo.CreateSar(ctx, sar)
	if err != nil {
		return nil, err
	}

	// Update alert status
	alert.Status = model.AlertStatusSARFiled
	alert.SarFiled = true
	alert.SarReference = &req.ReferenceNumber
	if err := s.repo.UpdateAlert(ctx, alert); err != nil {
		return nil, err
	}

	s.publisher.PublishSarFiled(ctx, alertID, req.ReferenceNumber, tenantID)
	s.logger.Info("Filed SAR",
		zap.String("sarId", sar.ID.String()),
		zap.String("alertId", alertID.String()),
		zap.String("tenant", tenantID))

	return sar, nil
}

// GetSarForAlert retrieves the SAR filing for a given alert.
func (s *Service) GetSarForAlert(ctx context.Context, alertID uuid.UUID, tenantID string) (*model.SarFiling, error) {
	sar, err := s.repo.GetSarByAlertID(ctx, alertID)
	if err != nil {
		return nil, err
	}
	if sar == nil {
		return nil, errors.NotFound("SAR filing not found for alertId: " + alertID.String())
	}
	return sar, nil
}

// ─── KYC ────────────────────────────────────────────────────────────────────

// CreateOrUpdateKyc creates or updates a KYC record.
func (s *Service) CreateOrUpdateKyc(ctx context.Context, req model.KycRequest, tenantID string) (*model.KycRecord, error) {
	if req.CustomerID == "" {
		return nil, errors.BadRequest("customer ID is required")
	}

	checkType := "FULL_KYC"
	if req.CheckType != nil {
		checkType = *req.CheckType
	}

	riskLevel := model.RiskLevelLow
	if req.RiskLevel != nil {
		riskLevel = *req.RiskLevel
	}

	rec := &model.KycRecord{
		TenantID:   tenantID,
		CustomerID: req.CustomerID,
		Status:     model.KycStatusInProgress,
		CheckType:  checkType,
		NationalID: req.NationalID,
		FullName:   req.FullName,
		Phone:      req.Phone,
		RiskLevel:  riskLevel,
	}

	rec, err := s.repo.UpsertKyc(ctx, rec)
	if err != nil {
		return nil, err
	}

	s.logger.Info("Upserted KYC record",
		zap.String("customerId", req.CustomerID),
		zap.String("tenant", tenantID))

	return rec, nil
}

// GetKyc retrieves a KYC record by customer ID and tenant.
func (s *Service) GetKyc(ctx context.Context, customerID, tenantID string) (*model.KycRecord, error) {
	rec, err := s.repo.GetKycByTenantAndCustomer(ctx, tenantID, customerID)
	if err != nil {
		return nil, err
	}
	if rec == nil {
		return nil, errors.NotFound("KYC record not found for customerId: " + customerID)
	}
	return rec, nil
}

// PassKyc marks a KYC record as passed and publishes an event.
func (s *Service) PassKyc(ctx context.Context, customerID, tenantID string) (*model.KycRecord, error) {
	rec, err := s.repo.GetKycByTenantAndCustomer(ctx, tenantID, customerID)
	if err != nil {
		return nil, err
	}
	if rec == nil {
		return nil, errors.NotFound("KYC record not found for customerId: " + customerID)
	}

	now := time.Now()
	rec.Status = model.KycStatusPassed
	rec.CheckedAt = &now

	if err := s.repo.UpdateKyc(ctx, rec); err != nil {
		return nil, err
	}

	s.publisher.PublishKycPassed(ctx, customerID, tenantID)
	s.logger.Info("KYC passed",
		zap.String("customerId", customerID),
		zap.String("tenant", tenantID))

	return rec, nil
}

// FailKyc marks a KYC record as failed and publishes an event.
func (s *Service) FailKyc(ctx context.Context, customerID, failureReason, tenantID string) (*model.KycRecord, error) {
	rec, err := s.repo.GetKycByTenantAndCustomer(ctx, tenantID, customerID)
	if err != nil {
		return nil, err
	}
	if rec == nil {
		return nil, errors.NotFound("KYC record not found for customerId: " + customerID)
	}

	now := time.Now()
	rec.Status = model.KycStatusFailed
	rec.FailureReason = &failureReason
	rec.CheckedAt = &now

	if err := s.repo.UpdateKyc(ctx, rec); err != nil {
		return nil, err
	}

	s.publisher.PublishKycFailed(ctx, customerID, failureReason, tenantID)
	s.logger.Info("KYC failed",
		zap.String("customerId", customerID),
		zap.String("tenant", tenantID))

	return rec, nil
}

// ─── Summary ────────────────────────────────────────────────────────────────

// GetSummary returns compliance summary counts.
func (s *Service) GetSummary(ctx context.Context, tenantID string) (*model.ComplianceSummaryResponse, error) {
	openAlerts, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.AlertStatusOpen)
	if err != nil {
		return nil, err
	}
	criticalAlerts, err := s.repo.CountAlertsBySeverityAndStatus(ctx, tenantID, model.AlertSeverityCritical, model.AlertStatusOpen)
	if err != nil {
		return nil, err
	}
	underReview, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.AlertStatusUnderReview)
	if err != nil {
		return nil, err
	}
	sarFiled, err := s.repo.CountAlertsByStatus(ctx, tenantID, model.AlertStatusSARFiled)
	if err != nil {
		return nil, err
	}
	pendingKyc, err := s.repo.CountKycByStatus(ctx, tenantID, model.KycStatusPending)
	if err != nil {
		return nil, err
	}
	failedKyc, err := s.repo.CountKycByStatus(ctx, tenantID, model.KycStatusFailed)
	if err != nil {
		return nil, err
	}

	return &model.ComplianceSummaryResponse{
		TenantID:          tenantID,
		OpenAlerts:        openAlerts,
		CriticalAlerts:    criticalAlerts,
		UnderReviewAlerts: underReview,
		SarFiledAlerts:    sarFiled,
		PendingKyc:        pendingKyc,
		FailedKyc:         failedKyc,
	}, nil
}

// ─── Events ─────────────────────────────────────────────────────────────────

// LogEvent records a compliance event.
func (s *Service) LogEvent(ctx context.Context, eventType, sourceService, subjectID, payload, tenantID string) error {
	evt := &model.ComplianceEvent{
		TenantID:      tenantID,
		EventType:     eventType,
		SourceService: strPtr(sourceService),
		SubjectID:     strPtr(subjectID),
		Payload:       strPtr(payload),
	}
	_, err := s.repo.CreateEvent(ctx, evt)
	return err
}

// ListEvents returns a paginated list of compliance events.
func (s *Service) ListEvents(ctx context.Context, tenantID string, page, size int) (dto.PageResponse, error) {
	events, total, err := s.repo.ListEvents(ctx, tenantID, page, size)
	if err != nil {
		return dto.PageResponse{}, err
	}
	if events == nil {
		events = []model.ComplianceEvent{}
	}
	return dto.NewPageResponse(events, page, size, total), nil
}

func strPtr(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
