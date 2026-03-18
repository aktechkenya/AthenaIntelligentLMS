package model

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// EventCategory classifies the domain event source.
type EventCategory string

const (
	EventCategoryLoanOrigination EventCategory = "LOAN_ORIGINATION"
	EventCategoryLoanManagement  EventCategory = "LOAN_MANAGEMENT"
	EventCategoryPayment         EventCategory = "PAYMENT"
	EventCategoryAccounting      EventCategory = "ACCOUNTING"
	EventCategoryFloat           EventCategory = "FLOAT"
	EventCategoryCollections     EventCategory = "COLLECTIONS"
	EventCategoryCompliance      EventCategory = "COMPLIANCE"
	EventCategoryAccount         EventCategory = "ACCOUNT"
	EventCategoryUnknown         EventCategory = "UNKNOWN"
)

// SnapshotPeriod defines the time granularity for portfolio snapshots.
type SnapshotPeriod string

const (
	SnapshotPeriodDaily   SnapshotPeriod = "DAILY"
	SnapshotPeriodWeekly  SnapshotPeriod = "WEEKLY"
	SnapshotPeriodMonthly SnapshotPeriod = "MONTHLY"
)

// ReportEvent stores a domain event for reporting/analytics.
type ReportEvent struct {
	ID            uuid.UUID        `json:"id"`
	TenantID      string           `json:"tenantId"`
	EventID       *string          `json:"eventId"`
	EventType     string           `json:"eventType"`
	EventCategory *string          `json:"eventCategory"`
	SourceService *string          `json:"sourceService"`
	SubjectID     *string          `json:"subjectId"`
	CustomerID    *int64           `json:"customerId"`
	Amount        *decimal.Decimal `json:"amount"`
	Currency      string           `json:"currency"`
	Payload       *string          `json:"payload"`
	OccurredAt    time.Time        `json:"occurredAt"`
	CreatedAt     time.Time        `json:"createdAt"`
}

// PortfolioSnapshot captures a point-in-time view of the loan portfolio.
type PortfolioSnapshot struct {
	ID               uuid.UUID       `json:"id"`
	TenantID         string          `json:"tenantId"`
	SnapshotDate     time.Time       `json:"snapshotDate"`
	Period           string          `json:"period"`
	TotalLoans       int             `json:"totalLoans"`
	ActiveLoans      int             `json:"activeLoans"`
	ClosedLoans      int             `json:"closedLoans"`
	DefaultedLoans   int             `json:"defaultedLoans"`
	TotalDisbursed   decimal.Decimal `json:"totalDisbursed"`
	TotalOutstanding decimal.Decimal `json:"totalOutstanding"`
	TotalCollected   decimal.Decimal `json:"totalCollected"`
	WatchLoans       int             `json:"watchLoans"`
	SubstandardLoans int             `json:"substandardLoans"`
	DoubtfulLoans    int             `json:"doubtfulLoans"`
	LossLoans        int             `json:"lossLoans"`
	Par30            decimal.Decimal `json:"par30"`
	Par90            decimal.Decimal `json:"par90"`
	CreatedAt        time.Time       `json:"createdAt"`
}

// EventMetric aggregates event counts and amounts by type per day.
type EventMetric struct {
	ID          uuid.UUID       `json:"id"`
	TenantID    string          `json:"tenantId"`
	MetricDate  time.Time       `json:"metricDate"`
	EventType   string          `json:"eventType"`
	EventCount  int64           `json:"eventCount"`
	TotalAmount decimal.Decimal `json:"totalAmount"`
	CreatedAt   time.Time       `json:"createdAt"`
	UpdatedAt   time.Time       `json:"updatedAt"`
}

// ReportEventResponse is the API response DTO for a report event.
type ReportEventResponse struct {
	ID            uuid.UUID        `json:"id"`
	TenantID      string           `json:"tenantId"`
	EventID       *string          `json:"eventId"`
	EventType     string           `json:"eventType"`
	EventCategory *string          `json:"eventCategory"`
	SourceService *string          `json:"sourceService"`
	SubjectID     *string          `json:"subjectId"`
	CustomerID    *int64           `json:"customerId"`
	Amount        *decimal.Decimal `json:"amount"`
	Currency      string           `json:"currency"`
	Payload       *string          `json:"payload"`
	OccurredAt    time.Time        `json:"occurredAt"`
	CreatedAt     time.Time        `json:"createdAt"`
}

// PortfolioSnapshotResponse is the API response DTO for a portfolio snapshot.
type PortfolioSnapshotResponse struct {
	ID               uuid.UUID       `json:"id"`
	TenantID         string          `json:"tenantId"`
	SnapshotDate     string          `json:"snapshotDate"`
	Period           string          `json:"period"`
	TotalLoans       int             `json:"totalLoans"`
	ActiveLoans      int             `json:"activeLoans"`
	ClosedLoans      int             `json:"closedLoans"`
	DefaultedLoans   int             `json:"defaultedLoans"`
	TotalDisbursed   decimal.Decimal `json:"totalDisbursed"`
	TotalOutstanding decimal.Decimal `json:"totalOutstanding"`
	TotalCollected   decimal.Decimal `json:"totalCollected"`
	WatchLoans       int             `json:"watchLoans"`
	SubstandardLoans int             `json:"substandardLoans"`
	DoubtfulLoans    int             `json:"doubtfulLoans"`
	LossLoans        int             `json:"lossLoans"`
	Par30            decimal.Decimal `json:"par30"`
	Par90            decimal.Decimal `json:"par90"`
	CreatedAt        time.Time       `json:"createdAt"`
}

// PortfolioSummaryResponse is the API response DTO for the portfolio summary.
type PortfolioSummaryResponse struct {
	TenantID         string          `json:"tenantId"`
	AsOfDate         string          `json:"asOfDate"`
	TotalLoans       int             `json:"totalLoans"`
	ActiveLoans      int             `json:"activeLoans"`
	ClosedLoans      int             `json:"closedLoans"`
	DefaultedLoans   int             `json:"defaultedLoans"`
	TotalDisbursed   decimal.Decimal `json:"totalDisbursed"`
	TotalOutstanding decimal.Decimal `json:"totalOutstanding"`
	TotalCollected   decimal.Decimal `json:"totalCollected"`
	Par30            decimal.Decimal `json:"par30"`
	Par90            decimal.Decimal `json:"par90"`
	WatchLoans       int             `json:"watchLoans"`
	SubstandardLoans int             `json:"substandardLoans"`
	DoubtfulLoans    int             `json:"doubtfulLoans"`
	LossLoans        int             `json:"lossLoans"`
}

// EventMetricResponse is the API response DTO for an event metric.
type EventMetricResponse struct {
	MetricDate  string          `json:"metricDate"`
	EventType   string          `json:"eventType"`
	EventCount  int64           `json:"eventCount"`
	TotalAmount decimal.Decimal `json:"totalAmount"`
}

// ToResponse converts a ReportEvent to its API response.
func (e *ReportEvent) ToResponse() ReportEventResponse {
	return ReportEventResponse{
		ID:            e.ID,
		TenantID:      e.TenantID,
		EventID:       e.EventID,
		EventType:     e.EventType,
		EventCategory: e.EventCategory,
		SourceService: e.SourceService,
		SubjectID:     e.SubjectID,
		CustomerID:    e.CustomerID,
		Amount:        e.Amount,
		Currency:      e.Currency,
		Payload:       e.Payload,
		OccurredAt:    e.OccurredAt,
		CreatedAt:     e.CreatedAt,
	}
}

// ToResponse converts a PortfolioSnapshot to its API response.
func (s *PortfolioSnapshot) ToResponse() PortfolioSnapshotResponse {
	return PortfolioSnapshotResponse{
		ID:               s.ID,
		TenantID:         s.TenantID,
		SnapshotDate:     s.SnapshotDate.Format("2006-01-02"),
		Period:           s.Period,
		TotalLoans:       s.TotalLoans,
		ActiveLoans:      s.ActiveLoans,
		ClosedLoans:      s.ClosedLoans,
		DefaultedLoans:   s.DefaultedLoans,
		TotalDisbursed:   s.TotalDisbursed,
		TotalOutstanding: s.TotalOutstanding,
		TotalCollected:   s.TotalCollected,
		WatchLoans:       s.WatchLoans,
		SubstandardLoans: s.SubstandardLoans,
		DoubtfulLoans:    s.DoubtfulLoans,
		LossLoans:        s.LossLoans,
		Par30:            s.Par30,
		Par90:            s.Par90,
		CreatedAt:        s.CreatedAt,
	}
}

// ToResponse converts an EventMetric to its API response.
func (m *EventMetric) ToResponse() EventMetricResponse {
	return EventMetricResponse{
		MetricDate:  m.MetricDate.Format("2006-01-02"),
		EventType:   m.EventType,
		EventCount:  m.EventCount,
		TotalAmount: m.TotalAmount,
	}
}
