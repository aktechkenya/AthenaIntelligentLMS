package service

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/collections/event"
	"github.com/athena-lms/go-services/internal/collections/model"
	"github.com/athena-lms/go-services/internal/collections/repository"
	"github.com/athena-lms/go-services/internal/common/errors"
)

// CollectionsService contains all business logic for the collections domain.
type CollectionsService struct {
	caseRepo   *repository.CollectionCaseRepository
	actionRepo *repository.CollectionActionRepository
	ptpRepo    *repository.PtpRepository
	publisher  *event.Publisher
	logger     *zap.Logger
}

// NewCollectionsService creates a new CollectionsService.
func NewCollectionsService(
	caseRepo *repository.CollectionCaseRepository,
	actionRepo *repository.CollectionActionRepository,
	ptpRepo *repository.PtpRepository,
	publisher *event.Publisher,
	logger *zap.Logger,
) *CollectionsService {
	return &CollectionsService{
		caseRepo:   caseRepo,
		actionRepo: actionRepo,
		ptpRepo:    ptpRepo,
		publisher:  publisher,
		logger:     logger,
	}
}

// -----------------------------------------------------------------------
// Case lifecycle
// -----------------------------------------------------------------------

// OpenOrUpdateCase opens a new collection case or updates an existing one.
func (s *CollectionsService) OpenOrUpdateCase(ctx context.Context, loanID uuid.UUID, customerID *string, dpd int, stage string, outstandingAmount *decimal.Decimal, tenantID string) error {
	existing, err := s.caseRepo.FindByLoanID(ctx, loanID)
	if err != nil {
		return fmt.Errorf("find by loan: %w", err)
	}

	if existing == nil {
		if dpd < 1 {
			return nil
		}
		priority := model.CasePriorityNormal
		if dpd > 90 {
			priority = model.CasePriorityCritical
		}
		amt := decimal.Zero
		if outstandingAmount != nil {
			amt = *outstandingAmount
		}
		newCase := &model.CollectionCase{
			TenantID:          tenantID,
			LoanID:            loanID,
			CustomerID:        customerID,
			CaseNumber:        generateCaseNumber(tenantID),
			Status:            model.CaseStatusOpen,
			Priority:          priority,
			CurrentDPD:        dpd,
			CurrentStage:      mapStage(stage),
			OutstandingAmount: amt,
		}
		saved, err := s.caseRepo.Save(ctx, newCase)
		if err != nil {
			return fmt.Errorf("save new case: %w", err)
		}
		s.publisher.PublishCaseCreated(ctx, saved.ID, loanID, tenantID)
		s.logger.Info("Opened new collection case", zap.String("caseNumber", saved.CaseNumber), zap.String("loanId", loanID.String()))
		return nil
	}

	existing.CurrentDPD = dpd
	if outstandingAmount != nil {
		existing.OutstandingAmount = *outstandingAmount
	}
	existing.CurrentStage = mapStage(stage)
	if dpd > 90 {
		existing.Priority = model.CasePriorityCritical
	}
	_, err = s.caseRepo.Save(ctx, existing)
	return err
}

// UpdateDPD updates the days past due for a loan's collection case.
func (s *CollectionsService) UpdateDPD(ctx context.Context, loanID uuid.UUID, dpd int, outstandingAmount *decimal.Decimal, tenantID string) error {
	existing, err := s.caseRepo.FindByLoanID(ctx, loanID)
	if err != nil {
		return fmt.Errorf("find by loan: %w", err)
	}

	if existing == nil {
		if dpd >= 1 {
			return s.OpenOrUpdateCase(ctx, loanID, nil, dpd, "WATCH", outstandingAmount, tenantID)
		}
		return nil
	}

	if existing.Status == model.CaseStatusClosed || existing.Status == model.CaseStatusWrittenOff {
		return nil
	}

	existing.CurrentDPD = dpd
	if outstandingAmount != nil {
		existing.OutstandingAmount = *outstandingAmount
	}
	if dpd > 90 {
		existing.Priority = model.CasePriorityCritical
	} else if dpd > 60 {
		existing.Priority = model.CasePriorityHigh
	}
	_, err = s.caseRepo.Save(ctx, existing)
	return err
}

// HandleStageChange handles a loan stage change event.
func (s *CollectionsService) HandleStageChange(ctx context.Context, loanID uuid.UUID, stage, tenantID string) error {
	existing, err := s.caseRepo.FindByLoanID(ctx, loanID)
	if err != nil {
		return fmt.Errorf("find by loan: %w", err)
	}
	if existing == nil {
		return nil
	}
	if existing.Status == model.CaseStatusClosed || existing.Status == model.CaseStatusWrittenOff {
		return nil
	}

	newStage := mapStage(stage)
	oldStage := existing.CurrentStage
	existing.CurrentStage = newStage

	if isWorseStage(oldStage, newStage) {
		_, err = s.caseRepo.Save(ctx, existing)
		if err != nil {
			return err
		}
		s.publisher.PublishCaseEscalated(ctx, existing.ID, loanID, newStage, tenantID)
		return nil
	}

	upperStage := strings.ToUpper(stage)
	if upperStage == "PERFORMING" || upperStage == "CLOSED" {
		now := time.Now().UTC()
		existing.Status = model.CaseStatusClosed
		existing.ClosedAt = &now
		_, err = s.caseRepo.Save(ctx, existing)
		if err != nil {
			return err
		}
		s.publisher.PublishCaseClosed(ctx, existing.ID, loanID, tenantID)
		return nil
	}

	_, err = s.caseRepo.Save(ctx, existing)
	return err
}

// GetCase returns a single collection case by ID and tenant.
func (s *CollectionsService) GetCase(ctx context.Context, id uuid.UUID, tenantID string) (*model.CollectionCaseResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, id)
	if err != nil {
		return nil, fmt.Errorf("find case: %w", err)
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", id)
	}
	resp := model.ToCaseResponse(c)
	return &resp, nil
}

// GetCaseByLoan returns the collection case for a given loan.
func (s *CollectionsService) GetCaseByLoan(ctx context.Context, loanID uuid.UUID, tenantID string) (*model.CollectionCaseResponse, error) {
	c, err := s.caseRepo.FindByLoanID(ctx, loanID)
	if err != nil {
		return nil, fmt.Errorf("find case by loan: %w", err)
	}
	if c == nil || c.TenantID != tenantID {
		return nil, errors.NotFound("No collection case for loan: " + loanID.String())
	}
	resp := model.ToCaseResponse(c)
	return &resp, nil
}

// CaseFilterParams holds optional filter parameters for listing cases.
type CaseFilterParams struct {
	Stage      string
	Priority   string
	AssignedTo string
	MinDPD     int
	MaxDPD     int
	Search     string
	Sort       string
	Dir        string
}

// hasFilters returns true if any filter field is set.
func (f CaseFilterParams) hasFilters() bool {
	return f.Stage != "" || f.Priority != "" || f.AssignedTo != "" ||
		f.MinDPD != 0 || f.MaxDPD != 0 || f.Search != "" || f.Sort != "" || f.Dir != ""
}

// ListCases returns a paginated list of collection cases with optional filtering.
func (s *CollectionsService) ListCases(ctx context.Context, tenantID string, status *model.CaseStatus, filters CaseFilterParams, page, size int) (*ListCasesResult, error) {
	offset := page * size

	// If any advanced filter is set, use FindByFilters
	if filters.hasFilters() || status != nil {
		rf := repository.CaseFilters{}
		if status != nil {
			st := string(*status)
			rf.Status = &st
		}
		if filters.Stage != "" {
			rf.Stage = &filters.Stage
		}
		if filters.Priority != "" {
			rf.Priority = &filters.Priority
		}
		if filters.AssignedTo != "" {
			rf.AssignedTo = &filters.AssignedTo
		}
		if filters.MinDPD != 0 {
			rf.MinDPD = &filters.MinDPD
		}
		if filters.MaxDPD != 0 {
			rf.MaxDPD = &filters.MaxDPD
		}
		if filters.Search != "" {
			rf.Search = &filters.Search
		}

		cases, total, err := s.caseRepo.FindByFilters(ctx, tenantID, rf, filters.Sort, filters.Dir, offset, size)
		if err != nil {
			return nil, err
		}

		responses := make([]model.CollectionCaseResponse, len(cases))
		for i, c := range cases {
			responses[i] = model.ToCaseResponse(c)
		}
		return &ListCasesResult{
			Content:       responses,
			Page:          page,
			Size:          size,
			TotalElements: total,
		}, nil
	}

	// Default: no filters
	var cases []*model.CollectionCase
	var total int64
	var err error

	cases, err = s.caseRepo.FindByTenantID(ctx, tenantID, offset, size)
	if err != nil {
		return nil, err
	}
	total, err = s.caseRepo.CountByTenantID(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	responses := make([]model.CollectionCaseResponse, len(cases))
	for i, c := range cases {
		responses[i] = model.ToCaseResponse(c)
	}

	return &ListCasesResult{
		Content:       responses,
		Page:          page,
		Size:          size,
		TotalElements: total,
	}, nil
}

// ListCasesResult holds the paginated result for ListCases.
type ListCasesResult struct {
	Content       []model.CollectionCaseResponse
	Page          int
	Size          int
	TotalElements int64
}

// UpdateCase updates mutable fields of a collection case.
func (s *CollectionsService) UpdateCase(ctx context.Context, id uuid.UUID, req model.UpdateCaseRequest, tenantID string) (*model.CollectionCaseResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, id)
	if err != nil {
		return nil, fmt.Errorf("find case: %w", err)
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", id)
	}
	if req.AssignedTo != nil {
		c.AssignedTo = req.AssignedTo
	}
	if req.Priority != nil {
		c.Priority = *req.Priority
	}
	if req.Notes != nil {
		c.Notes = req.Notes
	}
	saved, err := s.caseRepo.Save(ctx, c)
	if err != nil {
		return nil, err
	}
	resp := model.ToCaseResponse(saved)
	return &resp, nil
}

// CloseCase closes a collection case.
func (s *CollectionsService) CloseCase(ctx context.Context, caseID uuid.UUID, tenantID string) (*model.CollectionCaseResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, caseID)
	if err != nil {
		return nil, fmt.Errorf("find case: %w", err)
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", caseID)
	}
	now := time.Now().UTC()
	c.Status = model.CaseStatusClosed
	c.ClosedAt = &now
	saved, err := s.caseRepo.Save(ctx, c)
	if err != nil {
		return nil, err
	}
	s.publisher.PublishCaseClosed(ctx, saved.ID, saved.LoanID, tenantID)
	resp := model.ToCaseResponse(saved)
	return &resp, nil
}

// -----------------------------------------------------------------------
// Actions
// -----------------------------------------------------------------------

// AddAction adds a collection action to a case.
func (s *CollectionsService) AddAction(ctx context.Context, caseID uuid.UUID, req model.AddActionRequest, tenantID string) (*model.CollectionActionResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, caseID)
	if err != nil {
		return nil, fmt.Errorf("find case: %w", err)
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", caseID)
	}

	var nextActionDate *time.Time
	if req.NextActionDate != nil {
		t, err := time.Parse("2006-01-02", *req.NextActionDate)
		if err != nil {
			return nil, errors.BadRequest("Invalid nextActionDate format, expected YYYY-MM-DD")
		}
		nextActionDate = &t
	}

	action := &model.CollectionAction{
		TenantID:       tenantID,
		CaseID:         caseID,
		ActionType:     req.ActionType,
		Outcome:        req.Outcome,
		Notes:          req.Notes,
		ContactPerson:  req.ContactPerson,
		ContactMethod:  req.ContactMethod,
		PerformedBy:    req.PerformedBy,
		NextActionDate: nextActionDate,
	}

	saved, err := s.actionRepo.Save(ctx, action)
	if err != nil {
		return nil, fmt.Errorf("save action: %w", err)
	}

	now := time.Now().UTC()
	c.LastActionAt = &now
	if c.Status == model.CaseStatusOpen {
		c.Status = model.CaseStatusInProgress
	}
	_, err = s.caseRepo.Save(ctx, c)
	if err != nil {
		return nil, fmt.Errorf("update case after action: %w", err)
	}

	s.publisher.PublishActionTaken(ctx, caseID, req.ActionType, tenantID)
	resp := model.ToActionResponse(saved)
	return &resp, nil
}

// ListActions returns all actions for a case.
func (s *CollectionsService) ListActions(ctx context.Context, caseID uuid.UUID, tenantID string) ([]model.CollectionActionResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, caseID)
	if err != nil {
		return nil, err
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", caseID)
	}
	actions, err := s.actionRepo.FindByCaseIDOrderByPerformedAtDesc(ctx, caseID)
	if err != nil {
		return nil, err
	}
	responses := make([]model.CollectionActionResponse, len(actions))
	for i, a := range actions {
		responses[i] = model.ToActionResponse(a)
	}
	return responses, nil
}

// -----------------------------------------------------------------------
// PTPs
// -----------------------------------------------------------------------

// AddPtp adds a promise to pay to a case.
func (s *CollectionsService) AddPtp(ctx context.Context, caseID uuid.UUID, req model.AddPtpRequest, tenantID string) (*model.PtpResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, caseID)
	if err != nil {
		return nil, err
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", caseID)
	}

	promiseDate, err := time.Parse("2006-01-02", req.PromiseDate)
	if err != nil {
		return nil, errors.BadRequest("Invalid promiseDate format, expected YYYY-MM-DD")
	}

	ptp := &model.PromiseToPay{
		TenantID:       tenantID,
		CaseID:         caseID,
		PromisedAmount: req.PromisedAmount,
		PromiseDate:    promiseDate,
		Status:         model.PtpStatusPending,
		Notes:          req.Notes,
		CreatedBy:      req.CreatedBy,
	}

	saved, err := s.ptpRepo.Save(ctx, ptp)
	if err != nil {
		return nil, fmt.Errorf("save ptp: %w", err)
	}
	resp := model.ToPtpResponse(saved)
	return &resp, nil
}

// ListPtps returns all promises to pay for a case.
func (s *CollectionsService) ListPtps(ctx context.Context, caseID uuid.UUID, tenantID string) ([]model.PtpResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, caseID)
	if err != nil {
		return nil, err
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", caseID)
	}
	ptps, err := s.ptpRepo.FindByCaseIDOrderByCreatedAtDesc(ctx, caseID)
	if err != nil {
		return nil, err
	}
	responses := make([]model.PtpResponse, len(ptps))
	for i, p := range ptps {
		responses[i] = model.ToPtpResponse(p)
	}
	return responses, nil
}

// -----------------------------------------------------------------------
// Summary
// -----------------------------------------------------------------------

// GetSummary returns aggregate counts and amounts for the tenant's collection cases.
func (s *CollectionsService) GetSummary(ctx context.Context, tenantID string) (*model.CollectionSummaryResponse, error) {
	openCount, err := s.caseRepo.CountByTenantIDAndStatus(ctx, tenantID, model.CaseStatusOpen)
	if err != nil {
		return nil, err
	}
	inProgressCount, err := s.caseRepo.CountByTenantIDAndStatus(ctx, tenantID, model.CaseStatusInProgress)
	if err != nil {
		return nil, err
	}
	pendingLegalCount, err := s.caseRepo.CountByTenantIDAndStatus(ctx, tenantID, model.CaseStatusPendingLegal)
	if err != nil {
		return nil, err
	}
	watchCount, err := s.caseRepo.CountByTenantIDAndCurrentStage(ctx, tenantID, model.CollectionStageWatch)
	if err != nil {
		return nil, err
	}
	substandardCount, err := s.caseRepo.CountByTenantIDAndCurrentStage(ctx, tenantID, model.CollectionStageSubstandard)
	if err != nil {
		return nil, err
	}
	doubtfulCount, err := s.caseRepo.CountByTenantIDAndCurrentStage(ctx, tenantID, model.CollectionStageDoubtful)
	if err != nil {
		return nil, err
	}
	lossCount, err := s.caseRepo.CountByTenantIDAndCurrentStage(ctx, tenantID, model.CollectionStageLoss)
	if err != nil {
		return nil, err
	}
	criticalCount, err := s.caseRepo.CountByTenantIDAndPriority(ctx, tenantID, model.CasePriorityCritical)
	if err != nil {
		return nil, err
	}

	// Outstanding amounts
	totalOutstanding, err := s.caseRepo.SumTotalOutstanding(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	stageAmounts, err := s.caseRepo.SumOutstandingByStage(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	// PTP and follow-up counts
	pendingPtpCount, err := s.ptpRepo.CountPendingByTenantID(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	overdueFollowUpCount, err := s.actionRepo.CountOverdueFollowUps(ctx, tenantID)
	if err != nil {
		return nil, err
	}

	return &model.CollectionSummaryResponse{
		TotalOpenCases:         openCount + inProgressCount + pendingLegalCount,
		WatchCases:             watchCount,
		SubstandardCases:       substandardCount,
		DoubtfulCases:          doubtfulCount,
		LossCases:              lossCount,
		CriticalPriorityCases:  criticalCount,
		TotalOutstandingAmount: totalOutstanding,
		WatchAmount:            stageAmounts["WATCH"],
		SubstandardAmount:      stageAmounts["SUBSTANDARD"],
		DoubtfulAmount:         stageAmounts["DOUBTFUL"],
		LossAmount:             stageAmounts["LOSS"],
		PendingPtpCount:        pendingPtpCount,
		OverdueFollowUpCount:   overdueFollowUpCount,
		TenantID:               tenantID,
	}, nil
}

// GetCaseDetail returns a composite response with case, actions, and PTPs.
func (s *CollectionsService) GetCaseDetail(ctx context.Context, id uuid.UUID, tenantID string) (*model.CollectionCaseDetailResponse, error) {
	c, err := s.caseRepo.FindByTenantIDAndID(ctx, tenantID, id)
	if err != nil {
		return nil, fmt.Errorf("find case: %w", err)
	}
	if c == nil {
		return nil, errors.NotFoundResource("Collection case", id)
	}

	actions, err := s.actionRepo.FindByCaseIDOrderByPerformedAtDesc(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("find actions: %w", err)
	}

	ptps, err := s.ptpRepo.FindByCaseIDOrderByCreatedAtDesc(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("find ptps: %w", err)
	}

	actionResponses := make([]model.CollectionActionResponse, len(actions))
	for i, a := range actions {
		actionResponses[i] = model.ToActionResponse(a)
	}

	ptpResponses := make([]model.PtpResponse, len(ptps))
	for i, p := range ptps {
		ptpResponses[i] = model.ToPtpResponse(p)
	}

	return &model.CollectionCaseDetailResponse{
		Case:    model.ToCaseResponse(c),
		Actions: actionResponses,
		Ptps:    ptpResponses,
	}, nil
}

// -----------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------

func generateCaseNumber(tenantID string) string {
	prefix := "GEN"
	if tenantID != "" {
		upper := strings.ToUpper(tenantID)
		if len(upper) >= 3 {
			prefix = upper[:3]
		} else {
			prefix = upper
		}
	}
	return fmt.Sprintf("COL-%s-%d", prefix, time.Now().UnixMilli())
}

func mapStage(stage string) model.CollectionStage {
	if stage == "" {
		return model.CollectionStageWatch
	}
	switch strings.ToUpper(stage) {
	case "SUBSTANDARD":
		return model.CollectionStageSubstandard
	case "DOUBTFUL":
		return model.CollectionStageDoubtful
	case "LOSS":
		return model.CollectionStageLoss
	default:
		return model.CollectionStageWatch
	}
}

func isWorseStage(current, next model.CollectionStage) bool {
	return model.StageOrdinal(next) > model.StageOrdinal(current)
}
