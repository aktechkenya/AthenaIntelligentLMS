package model

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// CaseStatus represents the status of a collection case.
type CaseStatus string

const (
	CaseStatusOpen         CaseStatus = "OPEN"
	CaseStatusInProgress   CaseStatus = "IN_PROGRESS"
	CaseStatusPendingLegal CaseStatus = "PENDING_LEGAL"
	CaseStatusWrittenOff   CaseStatus = "WRITTEN_OFF"
	CaseStatusClosed       CaseStatus = "CLOSED"
)

// CasePriority represents the priority of a collection case.
type CasePriority string

const (
	CasePriorityLow      CasePriority = "LOW"
	CasePriorityNormal   CasePriority = "NORMAL"
	CasePriorityHigh     CasePriority = "HIGH"
	CasePriorityCritical CasePriority = "CRITICAL"
)

// CollectionStage represents the collection stage (asset classification).
type CollectionStage string

const (
	CollectionStageWatch        CollectionStage = "WATCH"
	CollectionStageSubstandard  CollectionStage = "SUBSTANDARD"
	CollectionStageDoubtful     CollectionStage = "DOUBTFUL"
	CollectionStageLoss         CollectionStage = "LOSS"
)

// stageOrdinal returns the ordinal of a CollectionStage for comparison.
func StageOrdinal(s CollectionStage) int {
	switch s {
	case CollectionStageWatch:
		return 0
	case CollectionStageSubstandard:
		return 1
	case CollectionStageDoubtful:
		return 2
	case CollectionStageLoss:
		return 3
	default:
		return 0
	}
}

// ActionType represents the type of collection action.
type ActionType string

const (
	ActionTypePhoneCall       ActionType = "PHONE_CALL"
	ActionTypeSMS             ActionType = "SMS"
	ActionTypeEmail           ActionType = "EMAIL"
	ActionTypeFieldVisit      ActionType = "FIELD_VISIT"
	ActionTypeLegalNotice     ActionType = "LEGAL_NOTICE"
	ActionTypeRestructureOffer ActionType = "RESTRUCTURE_OFFER"
	ActionTypeWriteOff        ActionType = "WRITE_OFF"
	ActionTypeOther           ActionType = "OTHER"
)

// ActionOutcome represents the outcome of a collection action.
type ActionOutcome string

const (
	ActionOutcomeContacted       ActionOutcome = "CONTACTED"
	ActionOutcomeNoAnswer        ActionOutcome = "NO_ANSWER"
	ActionOutcomePromiseReceived ActionOutcome = "PROMISE_RECEIVED"
	ActionOutcomeRefusedToPay    ActionOutcome = "REFUSED_TO_PAY"
	ActionOutcomePaymentReceived ActionOutcome = "PAYMENT_RECEIVED"
	ActionOutcomeEscalated       ActionOutcome = "ESCALATED"
	ActionOutcomeOther           ActionOutcome = "OTHER"
)

// PtpStatus represents the status of a promise to pay.
type PtpStatus string

const (
	PtpStatusPending   PtpStatus = "PENDING"
	PtpStatusFulfilled PtpStatus = "FULFILLED"
	PtpStatusBroken    PtpStatus = "BROKEN"
	PtpStatusCancelled PtpStatus = "CANCELLED"
)

// CollectionCase is the main collection case entity.
type CollectionCase struct {
	ID                uuid.UUID       `json:"id"`
	TenantID          string          `json:"tenantId"`
	LoanID            uuid.UUID       `json:"loanId"`
	CustomerID        *string         `json:"customerId"`
	CaseNumber        string          `json:"caseNumber"`
	Status            CaseStatus      `json:"status"`
	Priority          CasePriority    `json:"priority"`
	CurrentDPD        int             `json:"currentDpd"`
	CurrentStage      CollectionStage `json:"currentStage"`
	OutstandingAmount decimal.Decimal `json:"outstandingAmount"`
	AssignedTo        *string         `json:"assignedTo"`
	ProductType       *string         `json:"productType"`
	OpenedAt          time.Time       `json:"openedAt"`
	ClosedAt          *time.Time      `json:"closedAt"`
	LastActionAt      *time.Time      `json:"lastActionAt"`
	Notes             *string         `json:"notes"`
	CreatedAt         time.Time       `json:"createdAt"`
	UpdatedAt         time.Time       `json:"updatedAt"`
}

// CollectionAction represents an action taken on a collection case.
type CollectionAction struct {
	ID             uuid.UUID      `json:"id"`
	TenantID       string         `json:"tenantId"`
	CaseID         uuid.UUID      `json:"caseId"`
	ActionType     ActionType     `json:"actionType"`
	Outcome        *ActionOutcome `json:"outcome"`
	Notes          *string        `json:"notes"`
	ContactPerson  *string        `json:"contactPerson"`
	ContactMethod  *string        `json:"contactMethod"`
	PerformedBy    *string        `json:"performedBy"`
	PerformedAt    time.Time      `json:"performedAt"`
	NextActionDate *time.Time     `json:"nextActionDate"`
	CreatedAt      time.Time      `json:"createdAt"`
}

// PromiseToPay represents a promise to pay associated with a collection case.
type PromiseToPay struct {
	ID             uuid.UUID       `json:"id"`
	TenantID       string          `json:"tenantId"`
	CaseID         uuid.UUID       `json:"caseId"`
	PromisedAmount decimal.Decimal `json:"promisedAmount"`
	PromiseDate    time.Time       `json:"promiseDate"`
	Status         PtpStatus       `json:"status"`
	Notes          *string         `json:"notes"`
	CreatedBy      *string         `json:"createdBy"`
	FulfilledAt    *time.Time      `json:"fulfilledAt"`
	BrokenAt       *time.Time      `json:"brokenAt"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// ---------- Request DTOs ----------

// AddActionRequest is the request body for adding an action to a case.
type AddActionRequest struct {
	ActionType    ActionType     `json:"actionType"`
	Outcome       *ActionOutcome `json:"outcome"`
	Notes         *string        `json:"notes"`
	ContactPerson *string        `json:"contactPerson"`
	ContactMethod *string        `json:"contactMethod"`
	PerformedBy   *string        `json:"performedBy"`
	NextActionDate *string       `json:"nextActionDate"` // "2006-01-02"
}

// AddPtpRequest is the request body for adding a promise to pay.
type AddPtpRequest struct {
	PromisedAmount decimal.Decimal `json:"promisedAmount"`
	PromiseDate    string          `json:"promiseDate"` // "2006-01-02"
	Notes          *string         `json:"notes"`
	CreatedBy      *string         `json:"createdBy"`
}

// UpdateCaseRequest is the request body for updating a collection case.
type UpdateCaseRequest struct {
	AssignedTo *string       `json:"assignedTo"`
	Priority   *CasePriority `json:"priority"`
	Notes      *string       `json:"notes"`
}

// ---------- Response DTOs ----------

// CollectionCaseResponse is the JSON response for a collection case.
type CollectionCaseResponse struct {
	ID                uuid.UUID       `json:"id"`
	TenantID          string          `json:"tenantId"`
	LoanID            uuid.UUID       `json:"loanId"`
	CustomerID        *string         `json:"customerId"`
	CaseNumber        string          `json:"caseNumber"`
	Status            CaseStatus      `json:"status"`
	Priority          CasePriority    `json:"priority"`
	CurrentDPD        int             `json:"currentDpd"`
	CurrentStage      CollectionStage `json:"currentStage"`
	OutstandingAmount decimal.Decimal `json:"outstandingAmount"`
	AssignedTo        *string         `json:"assignedTo"`
	ProductType       *string         `json:"productType"`
	OpenedAt          time.Time       `json:"openedAt"`
	ClosedAt          *time.Time      `json:"closedAt"`
	LastActionAt      *time.Time      `json:"lastActionAt"`
	Notes             *string         `json:"notes"`
	CreatedAt         time.Time       `json:"createdAt"`
	UpdatedAt         time.Time       `json:"updatedAt"`
}

// CollectionActionResponse is the JSON response for a collection action.
type CollectionActionResponse struct {
	ID             uuid.UUID      `json:"id"`
	CaseID         uuid.UUID      `json:"caseId"`
	ActionType     ActionType     `json:"actionType"`
	Outcome        *ActionOutcome `json:"outcome"`
	Notes          *string        `json:"notes"`
	ContactPerson  *string        `json:"contactPerson"`
	ContactMethod  *string        `json:"contactMethod"`
	PerformedBy    *string        `json:"performedBy"`
	PerformedAt    time.Time      `json:"performedAt"`
	NextActionDate *time.Time     `json:"nextActionDate"`
	CreatedAt      time.Time      `json:"createdAt"`
}

// PtpResponse is the JSON response for a promise to pay.
type PtpResponse struct {
	ID             uuid.UUID       `json:"id"`
	CaseID         uuid.UUID       `json:"caseId"`
	PromisedAmount decimal.Decimal `json:"promisedAmount"`
	PromiseDate    time.Time       `json:"promiseDate"`
	Status         PtpStatus       `json:"status"`
	Notes          *string         `json:"notes"`
	CreatedBy      *string         `json:"createdBy"`
	FulfilledAt    *time.Time      `json:"fulfilledAt"`
	BrokenAt       *time.Time      `json:"brokenAt"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// CollectionSummaryResponse is the JSON response for the summary endpoint.
type CollectionSummaryResponse struct {
	TotalOpenCases         int64           `json:"totalOpenCases"`
	WatchCases             int64           `json:"watchCases"`
	SubstandardCases       int64           `json:"substandardCases"`
	DoubtfulCases          int64           `json:"doubtfulCases"`
	LossCases              int64           `json:"lossCases"`
	CriticalPriorityCases  int64           `json:"criticalPriorityCases"`
	TotalOutstandingAmount decimal.Decimal `json:"totalOutstandingAmount"`
	WatchAmount            decimal.Decimal `json:"watchAmount"`
	SubstandardAmount      decimal.Decimal `json:"substandardAmount"`
	DoubtfulAmount         decimal.Decimal `json:"doubtfulAmount"`
	LossAmount             decimal.Decimal `json:"lossAmount"`
	PendingPtpCount        int64           `json:"pendingPtpCount"`
	OverdueFollowUpCount   int64           `json:"overdueFollowUpCount"`
	TenantID               string          `json:"tenantId"`
}

// CollectionCaseDetailResponse is the composite response for a case with its actions and PTPs.
type CollectionCaseDetailResponse struct {
	Case    CollectionCaseResponse   `json:"case"`
	Actions []CollectionActionResponse `json:"actions"`
	Ptps    []PtpResponse            `json:"ptps"`
}

// ---------- Helper converters ----------

// ToCaseResponse converts a CollectionCase entity to its response DTO.
func ToCaseResponse(c *CollectionCase) CollectionCaseResponse {
	return CollectionCaseResponse{
		ID:                c.ID,
		TenantID:          c.TenantID,
		LoanID:            c.LoanID,
		CustomerID:        c.CustomerID,
		CaseNumber:        c.CaseNumber,
		Status:            c.Status,
		Priority:          c.Priority,
		CurrentDPD:        c.CurrentDPD,
		CurrentStage:      c.CurrentStage,
		OutstandingAmount: c.OutstandingAmount,
		AssignedTo:        c.AssignedTo,
		ProductType:       c.ProductType,
		OpenedAt:          c.OpenedAt,
		ClosedAt:          c.ClosedAt,
		LastActionAt:      c.LastActionAt,
		Notes:             c.Notes,
		CreatedAt:         c.CreatedAt,
		UpdatedAt:         c.UpdatedAt,
	}
}

// ToActionResponse converts a CollectionAction entity to its response DTO.
func ToActionResponse(a *CollectionAction) CollectionActionResponse {
	return CollectionActionResponse{
		ID:             a.ID,
		CaseID:         a.CaseID,
		ActionType:     a.ActionType,
		Outcome:        a.Outcome,
		Notes:          a.Notes,
		ContactPerson:  a.ContactPerson,
		ContactMethod:  a.ContactMethod,
		PerformedBy:    a.PerformedBy,
		PerformedAt:    a.PerformedAt,
		NextActionDate: a.NextActionDate,
		CreatedAt:      a.CreatedAt,
	}
}

// ToPtpResponse converts a PromiseToPay entity to its response DTO.
func ToPtpResponse(p *PromiseToPay) PtpResponse {
	return PtpResponse{
		ID:             p.ID,
		CaseID:         p.CaseID,
		PromisedAmount: p.PromisedAmount,
		PromiseDate:    p.PromiseDate,
		Status:         p.Status,
		Notes:          p.Notes,
		CreatedBy:      p.CreatedBy,
		FulfilledAt:    p.FulfilledAt,
		BrokenAt:       p.BrokenAt,
		CreatedAt:      p.CreatedAt,
		UpdatedAt:      p.UpdatedAt,
	}
}

// ---------- Collection Strategy ----------

// CollectionStrategy defines an automated action recommendation rule.
type CollectionStrategy struct {
	ID          uuid.UUID  `json:"id"`
	TenantID    string     `json:"tenantId"`
	Name        string     `json:"name"`
	ProductType *string    `json:"productType"`
	DpdFrom     int        `json:"dpdFrom"`
	DpdTo       int        `json:"dpdTo"`
	ActionType  ActionType `json:"actionType"`
	Priority    int        `json:"priority"`
	IsActive    bool       `json:"isActive"`
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
}

// StrategyResponse is the JSON response for a collection strategy.
type StrategyResponse struct {
	ID          uuid.UUID  `json:"id"`
	TenantID    string     `json:"tenantId"`
	Name        string     `json:"name"`
	ProductType *string    `json:"productType"`
	DpdFrom     int        `json:"dpdFrom"`
	DpdTo       int        `json:"dpdTo"`
	ActionType  ActionType `json:"actionType"`
	Priority    int        `json:"priority"`
	IsActive    bool       `json:"isActive"`
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
}

// CreateStrategyRequest is the request body for creating a strategy.
type CreateStrategyRequest struct {
	Name        string     `json:"name"`
	ProductType *string    `json:"productType"`
	DpdFrom     int        `json:"dpdFrom"`
	DpdTo       int        `json:"dpdTo"`
	ActionType  ActionType `json:"actionType"`
	Priority    int        `json:"priority"`
	IsActive    *bool      `json:"isActive"`
}

// UpdateStrategyRequest is the request body for updating a strategy.
type UpdateStrategyRequest struct {
	Name        *string     `json:"name"`
	ProductType *string     `json:"productType"`
	DpdFrom     *int        `json:"dpdFrom"`
	DpdTo       *int        `json:"dpdTo"`
	ActionType  *ActionType `json:"actionType"`
	Priority    *int        `json:"priority"`
	IsActive    *bool       `json:"isActive"`
}

// RecommendedAction is a strategy-driven recommended action for a case.
type RecommendedAction struct {
	StrategyID   uuid.UUID  `json:"strategyId"`
	StrategyName string     `json:"strategyName"`
	ActionType   ActionType `json:"actionType"`
	Priority     int        `json:"priority"`
}

// ToStrategyResponse converts a CollectionStrategy entity to its response DTO.
func ToStrategyResponse(s *CollectionStrategy) StrategyResponse {
	return StrategyResponse{
		ID:          s.ID,
		TenantID:    s.TenantID,
		Name:        s.Name,
		ProductType: s.ProductType,
		DpdFrom:     s.DpdFrom,
		DpdTo:       s.DpdTo,
		ActionType:  s.ActionType,
		Priority:    s.Priority,
		IsActive:    s.IsActive,
		CreatedAt:   s.CreatedAt,
		UpdatedAt:   s.UpdatedAt,
	}
}
