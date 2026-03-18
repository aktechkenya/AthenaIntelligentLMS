package model

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// ─── Enums ──────────────────────────────────────────────────────────────────

type AlertSeverity string

const (
	SeverityLow      AlertSeverity = "LOW"
	SeverityMedium   AlertSeverity = "MEDIUM"
	SeverityHigh     AlertSeverity = "HIGH"
	SeverityCritical AlertSeverity = "CRITICAL"
)

type AlertSource string

const (
	SourceRuleEngine AlertSource = "RULE_ENGINE"
	SourceMLModel    AlertSource = "ML_MODEL"
	SourceManual     AlertSource = "MANUAL"
	SourceWatchlist  AlertSource = "WATCHLIST"
)

type AlertStatus string

const (
	StatusOpen           AlertStatus = "OPEN"
	StatusUnderReview    AlertStatus = "UNDER_REVIEW"
	StatusEscalated      AlertStatus = "ESCALATED"
	StatusConfirmedFraud AlertStatus = "CONFIRMED_FRAUD"
	StatusFalsePositive  AlertStatus = "FALSE_POSITIVE"
	StatusClosed         AlertStatus = "CLOSED"
)

type AlertType string

const (
	AlertLargeTransaction     AlertType = "LARGE_TRANSACTION"
	AlertStructuring          AlertType = "STRUCTURING"
	AlertHighVelocity         AlertType = "HIGH_VELOCITY"
	AlertRapidFundMovement    AlertType = "RAPID_FUND_MOVEMENT"
	AlertApplicationStacking  AlertType = "APPLICATION_STACKING"
	AlertLoanCycling          AlertType = "LOAN_CYCLING"
	AlertEarlyPayoff          AlertType = "EARLY_PAYOFF"
	AlertDormantReactivation  AlertType = "DORMANT_REACTIVATION"
	AlertKYCBypass            AlertType = "KYC_BYPASS"
	AlertOverdraftAbuse       AlertType = "OVERDRAFT_ABUSE"
	AlertBNPLAbuse            AlertType = "BNPL_ABUSE"
	AlertPaymentReversal      AlertType = "PAYMENT_REVERSAL"
	AlertOverpayment          AlertType = "OVERPAYMENT"
	AlertSuspiciousWriteoff   AlertType = "SUSPICIOUS_WRITEOFF"
	AlertWatchlistMatch       AlertType = "WATCHLIST_MATCH"
	AlertRoundAmountPattern   AlertType = "ROUND_AMOUNT_PATTERN"
	AlertPromiseToPayGaming   AlertType = "PROMISE_TO_PAY_GAMING"
	AlertMLAnomaly            AlertType = "ML_ANOMALY"
)

type CaseStatus string

const (
	CaseOpen               CaseStatus = "OPEN"
	CaseInvestigating      CaseStatus = "INVESTIGATING"
	CasePendingReview      CaseStatus = "PENDING_REVIEW"
	CaseEscalated          CaseStatus = "ESCALATED"
	CaseClosedConfirmed    CaseStatus = "CLOSED_CONFIRMED"
	CaseClosedFalsePositive CaseStatus = "CLOSED_FALSE_POSITIVE"
	CaseClosedInconclusive CaseStatus = "CLOSED_INCONCLUSIVE"
)

type RiskLevel string

const (
	RiskLow      RiskLevel = "LOW"
	RiskMedium   RiskLevel = "MEDIUM"
	RiskHigh     RiskLevel = "HIGH"
	RiskCritical RiskLevel = "CRITICAL"
)

type RuleCategory string

const (
	CategoryTransaction RuleCategory = "TRANSACTION"
	CategoryAML         RuleCategory = "AML"
	CategoryVelocity    RuleCategory = "VELOCITY"
	CategoryApplication RuleCategory = "APPLICATION"
	CategoryAccount     RuleCategory = "ACCOUNT"
	CategoryCompliance  RuleCategory = "COMPLIANCE"
	CategoryOverdraft   RuleCategory = "OVERDRAFT"
	CategoryInternal    RuleCategory = "INTERNAL"
	CategoryCollections RuleCategory = "COLLECTIONS"
)

type SarReportType string

const (
	SarTypeSAR SarReportType = "SAR"
	SarTypeCTR SarReportType = "CTR"
)

type SarStatus string

const (
	SarDraft         SarStatus = "DRAFT"
	SarPendingReview SarStatus = "PENDING_REVIEW"
	SarApproved      SarStatus = "APPROVED"
	SarFiled         SarStatus = "FILED"
	SarRejected      SarStatus = "REJECTED"
)

type WatchlistType string

const (
	WatchlistPEP             WatchlistType = "PEP"
	WatchlistSanctions       WatchlistType = "SANCTIONS"
	WatchlistInternalBlacklist WatchlistType = "INTERNAL_BLACKLIST"
	WatchlistAdverseMedia    WatchlistType = "ADVERSE_MEDIA"
)

// ─── Entities ───────────────────────────────────────────────────────────────

type FraudRule struct {
	ID          uuid.UUID              `json:"id" db:"id"`
	TenantID    string                 `json:"tenantId" db:"tenant_id"`
	RuleCode    string                 `json:"ruleCode" db:"rule_code"`
	RuleName    string                 `json:"ruleName" db:"rule_name"`
	Description *string                `json:"description" db:"description"`
	Category    RuleCategory           `json:"category" db:"category"`
	Severity    AlertSeverity          `json:"severity" db:"severity"`
	EventTypes  string                 `json:"eventTypes" db:"event_types"`
	Enabled     bool                   `json:"enabled" db:"enabled"`
	Parameters  map[string]interface{} `json:"parameters" db:"parameters"`
	CreatedAt   time.Time              `json:"createdAt" db:"created_at"`
	UpdatedAt   time.Time              `json:"updatedAt" db:"updated_at"`
}

// AppliesTo checks if the rule applies to the given event type.
func (r *FraudRule) AppliesTo(eventType string) bool {
	if r.EventTypes == "" {
		return false
	}
	// Simple string contains check (event types are comma-separated)
	for _, et := range splitCSV(r.EventTypes) {
		if et == eventType {
			return true
		}
	}
	return false
}

func splitCSV(s string) []string {
	var result []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == ',' {
			result = append(result, s[start:i])
			start = i + 1
		}
	}
	result = append(result, s[start:])
	return result
}

type FraudAlert struct {
	ID                    uuid.UUID              `json:"id" db:"id"`
	TenantID              string                 `json:"tenantId" db:"tenant_id"`
	AlertType             AlertType              `json:"alertType" db:"alert_type"`
	Severity              AlertSeverity          `json:"severity" db:"severity"`
	Status                AlertStatus            `json:"status" db:"status"`
	Source                AlertSource            `json:"source" db:"source"`
	RuleCode              *string                `json:"ruleCode" db:"rule_code"`
	CustomerID            *string                `json:"customerId" db:"customer_id"`
	SubjectType           string                 `json:"subjectType" db:"subject_type"`
	SubjectID             string                 `json:"subjectId" db:"subject_id"`
	Description           string                 `json:"description" db:"description"`
	TriggerEvent          *string                `json:"triggerEvent" db:"trigger_event"`
	TriggerAmount         *decimal.Decimal       `json:"triggerAmount" db:"trigger_amount"`
	RiskScore             *decimal.Decimal       `json:"riskScore" db:"risk_score"`
	ModelVersion          *string                `json:"modelVersion" db:"model_version"`
	Explanation           json.RawMessage        `json:"explanation" db:"explanation"`
	Escalated             bool                   `json:"escalated" db:"escalated"`
	EscalatedToCompliance bool                   `json:"escalatedToCompliance" db:"escalated_to_compliance"`
	ComplianceAlertID     *uuid.UUID             `json:"complianceAlertId" db:"compliance_alert_id"`
	AssignedTo            *string                `json:"assignedTo" db:"assigned_to"`
	ResolvedBy            *string                `json:"resolvedBy" db:"resolved_by"`
	ResolvedAt            *time.Time             `json:"resolvedAt" db:"resolved_at"`
	Resolution            *string                `json:"resolution" db:"resolution"`
	ResolutionNotes       *string                `json:"resolutionNotes" db:"resolution_notes"`
	CreatedAt             time.Time              `json:"createdAt" db:"created_at"`
	UpdatedAt             time.Time              `json:"updatedAt" db:"updated_at"`
}

type VelocityCounter struct {
	ID          uuid.UUID       `json:"id" db:"id"`
	TenantID    string          `json:"tenantId" db:"tenant_id"`
	CustomerID  string          `json:"customerId" db:"customer_id"`
	CounterType string          `json:"counterType" db:"counter_type"`
	WindowStart time.Time       `json:"windowStart" db:"window_start"`
	WindowEnd   time.Time       `json:"windowEnd" db:"window_end"`
	Count       int             `json:"count" db:"count"`
	TotalAmount decimal.Decimal `json:"totalAmount" db:"total_amount"`
	CreatedAt   time.Time       `json:"createdAt" db:"created_at"`
	UpdatedAt   time.Time       `json:"updatedAt" db:"updated_at"`
}

type CustomerRiskProfile struct {
	ID                    uuid.UUID              `json:"id" db:"id"`
	TenantID              string                 `json:"tenantId" db:"tenant_id"`
	CustomerID            string                 `json:"customerId" db:"customer_id"`
	RiskScore             decimal.Decimal        `json:"riskScore" db:"risk_score"`
	RiskLevel             RiskLevel              `json:"riskLevel" db:"risk_level"`
	TotalAlerts           int                    `json:"totalAlerts" db:"total_alerts"`
	OpenAlerts            int                    `json:"openAlerts" db:"open_alerts"`
	ConfirmedFraud        int                    `json:"confirmedFraud" db:"confirmed_fraud"`
	FalsePositives        int                    `json:"falsePositives" db:"false_positives"`
	AvgTransactionAmount  *decimal.Decimal       `json:"avgTransactionAmount" db:"avg_transaction_amount"`
	TransactionCount30d   int                    `json:"transactionCount30d" db:"transaction_count_30d"`
	LastAlertAt           *time.Time             `json:"lastAlertAt" db:"last_alert_at"`
	LastScoredAt          *time.Time             `json:"lastScoredAt" db:"last_scored_at"`
	Factors               map[string]interface{} `json:"factors" db:"factors"`
	CreatedAt             time.Time              `json:"createdAt" db:"created_at"`
	UpdatedAt             time.Time              `json:"updatedAt" db:"updated_at"`
}

type FraudEvent struct {
	ID             uuid.UUID              `json:"id" db:"id"`
	TenantID       string                 `json:"tenantId" db:"tenant_id"`
	EventType      string                 `json:"eventType" db:"event_type"`
	SourceService  *string                `json:"sourceService" db:"source_service"`
	CustomerID     *string                `json:"customerId" db:"customer_id"`
	SubjectID      *string                `json:"subjectId" db:"subject_id"`
	Amount         *decimal.Decimal       `json:"amount" db:"amount"`
	RiskScore      *decimal.Decimal       `json:"riskScore" db:"risk_score"`
	RulesTriggered *string                `json:"rulesTriggered" db:"rules_triggered"`
	Payload        json.RawMessage        `json:"payload" db:"payload"`
	ProcessedAt    time.Time              `json:"processedAt" db:"processed_at"`
}

type WatchlistEntry struct {
	ID        uuid.UUID     `json:"id" db:"id"`
	TenantID  string        `json:"tenantId" db:"tenant_id"`
	ListType  WatchlistType `json:"listType" db:"list_type"`
	EntryType string        `json:"entryType" db:"entry_type"`
	Name      *string       `json:"name" db:"name"`
	NationalID *string      `json:"nationalId" db:"national_id"`
	Phone     *string       `json:"phone" db:"phone"`
	Reason    *string       `json:"reason" db:"reason"`
	Source    *string       `json:"source" db:"source"`
	Active    bool          `json:"active" db:"active"`
	ExpiresAt *time.Time    `json:"expiresAt" db:"expires_at"`
	CreatedAt time.Time     `json:"createdAt" db:"created_at"`
	UpdatedAt time.Time     `json:"updatedAt" db:"updated_at"`
}

type FraudCase struct {
	ID             uuid.UUID        `json:"id" db:"id"`
	TenantID       string           `json:"tenantId" db:"tenant_id"`
	CaseNumber     string           `json:"caseNumber" db:"case_number"`
	Title          string           `json:"title" db:"title"`
	Description    *string          `json:"description" db:"description"`
	Status         CaseStatus       `json:"status" db:"status"`
	Priority       AlertSeverity    `json:"priority" db:"priority"`
	CustomerID     *string          `json:"customerId" db:"customer_id"`
	AssignedTo     *string          `json:"assignedTo" db:"assigned_to"`
	TotalExposure  *decimal.Decimal `json:"totalExposure" db:"total_exposure"`
	ConfirmedLoss  decimal.Decimal  `json:"confirmedLoss" db:"confirmed_loss"`
	Tags           json.RawMessage  `json:"tags" db:"tags"`
	SLADeadline    *time.Time       `json:"slaDeadline" db:"sla_deadline"`
	SLABreached    bool             `json:"slaBreached" db:"sla_breached"`
	ClosedAt       *time.Time       `json:"closedAt" db:"closed_at"`
	ClosedBy       *string          `json:"closedBy" db:"closed_by"`
	Outcome        *string          `json:"outcome" db:"outcome"`
	CreatedAt      time.Time        `json:"createdAt" db:"created_at"`
	UpdatedAt      time.Time        `json:"updatedAt" db:"updated_at"`
	// Not stored in main table; loaded separately
	AlertIDs []uuid.UUID       `json:"alertIds" db:"-"`
	Notes    []CaseNote        `json:"notes,omitempty" db:"-"`
}

type CaseNote struct {
	ID        uuid.UUID `json:"id" db:"id"`
	CaseID    uuid.UUID `json:"caseId" db:"case_id"`
	TenantID  string    `json:"tenantId" db:"tenant_id"`
	Author    string    `json:"author" db:"author"`
	Content   string    `json:"content" db:"content"`
	NoteType  string    `json:"noteType" db:"note_type"`
	CreatedAt time.Time `json:"createdAt" db:"created_at"`
}

type AuditLog struct {
	ID          uuid.UUID              `json:"id" db:"id"`
	TenantID    string                 `json:"tenantId" db:"tenant_id"`
	Action      string                 `json:"action" db:"action"`
	EntityType  string                 `json:"entityType" db:"entity_type"`
	EntityID    uuid.UUID              `json:"entityId" db:"entity_id"`
	PerformedBy string                 `json:"performedBy" db:"performed_by"`
	Description *string                `json:"description" db:"description"`
	Changes     map[string]interface{} `json:"changes" db:"changes"`
	CreatedAt   time.Time              `json:"createdAt" db:"created_at"`
}

type NetworkLink struct {
	ID          uuid.UUID `json:"id" db:"id"`
	TenantID    string    `json:"tenantId" db:"tenant_id"`
	CustomerIDA string    `json:"customerIdA" db:"customer_id_a"`
	CustomerIDB string    `json:"customerIdB" db:"customer_id_b"`
	LinkType    string    `json:"linkType" db:"link_type"`
	LinkValue   string    `json:"linkValue" db:"link_value"`
	Strength    int       `json:"strength" db:"strength"`
	Flagged     bool      `json:"flagged" db:"flagged"`
	CreatedAt   time.Time `json:"createdAt" db:"created_at"`
}

type SarReport struct {
	ID                uuid.UUID        `json:"id" db:"id"`
	TenantID          string           `json:"tenantId" db:"tenant_id"`
	ReportNumber      string           `json:"reportNumber" db:"report_number"`
	ReportType        SarReportType    `json:"reportType" db:"report_type"`
	Status            SarStatus        `json:"status" db:"status"`
	SubjectCustomerID *string          `json:"subjectCustomerId" db:"subject_customer_id"`
	SubjectName       *string          `json:"subjectName" db:"subject_name"`
	SubjectNationalID *string          `json:"subjectNationalId" db:"subject_national_id"`
	Narrative         *string          `json:"narrative" db:"narrative"`
	SuspiciousAmount  *decimal.Decimal `json:"suspiciousAmount" db:"suspicious_amount"`
	ActivityStartDate *time.Time       `json:"activityStartDate" db:"activity_start_date"`
	ActivityEndDate   *time.Time       `json:"activityEndDate" db:"activity_end_date"`
	CaseID            *uuid.UUID       `json:"caseId" db:"case_id"`
	PreparedBy        *string          `json:"preparedBy" db:"prepared_by"`
	ReviewedBy        *string          `json:"reviewedBy" db:"reviewed_by"`
	FiledBy           *string          `json:"filedBy" db:"filed_by"`
	FiledAt           *time.Time       `json:"filedAt" db:"filed_at"`
	FilingReference   *string          `json:"filingReference" db:"filing_reference"`
	Regulator         string           `json:"regulator" db:"regulator"`
	FilingDeadline    *time.Time       `json:"filingDeadline" db:"filing_deadline"`
	Metadata          json.RawMessage  `json:"metadata" db:"metadata"`
	CreatedAt         time.Time        `json:"createdAt" db:"created_at"`
	UpdatedAt         time.Time        `json:"updatedAt" db:"updated_at"`
	// Loaded separately
	AlertIDs []uuid.UUID `json:"alertIds" db:"-"`
}

type ScoringHistory struct {
	ID             uuid.UUID        `json:"id" db:"id"`
	TenantID       string           `json:"tenantId" db:"tenant_id"`
	CustomerID     string           `json:"customerId" db:"customer_id"`
	EventType      *string          `json:"eventType" db:"event_type"`
	Amount         *decimal.Decimal `json:"amount" db:"amount"`
	MLScore        float64          `json:"mlScore" db:"ml_score"`
	RiskLevel      string           `json:"riskLevel" db:"risk_level"`
	ModelAvailable bool             `json:"modelAvailable" db:"model_available"`
	LatencyMs      float64          `json:"latencyMs" db:"latency_ms"`
	RuleScore      float64          `json:"ruleScore" db:"rule_score"`
	AnomalyScore   float64          `json:"anomalyScore" db:"anomaly_score"`
	LGBMScore      float64          `json:"lgbmScore" db:"lgbm_score"`
	ModelDetails   *string          `json:"modelDetails" db:"model_details"`
	CreatedAt      time.Time        `json:"createdAt" db:"created_at"`
}

// ─── Request DTOs ───────────────────────────────────────────────────────────

type ResolveAlertRequest struct {
	ResolvedBy     string `json:"resolvedBy"`
	ConfirmedFraud *bool  `json:"confirmedFraud"`
	Notes          string `json:"notes"`
}

type AssignAlertRequest struct {
	Assignee string `json:"assignee"`
}

type BulkAlertActionRequest struct {
	AlertIDs    []uuid.UUID `json:"alertIds"`
	PerformedBy string      `json:"performedBy"`
	Notes       string      `json:"notes"`
}

type UpdateRuleRequest struct {
	Severity   *string                `json:"severity"`
	Enabled    *bool                  `json:"enabled"`
	Parameters map[string]interface{} `json:"parameters"`
}

type CreateCaseRequest struct {
	Title         string           `json:"title"`
	Description   string           `json:"description"`
	Priority      string           `json:"priority"`
	CustomerID    string           `json:"customerId"`
	AssignedTo    string           `json:"assignedTo"`
	TotalExposure *decimal.Decimal `json:"totalExposure"`
	AlertIDs      []uuid.UUID      `json:"alertIds"`
	Tags          []string         `json:"tags"`
}

type UpdateCaseRequest struct {
	Status        *string          `json:"status"`
	Priority      *string          `json:"priority"`
	AssignedTo    *string          `json:"assignedTo"`
	TotalExposure *decimal.Decimal `json:"totalExposure"`
	ConfirmedLoss *decimal.Decimal `json:"confirmedLoss"`
	AlertIDs      []uuid.UUID      `json:"alertIds"`
	Tags          []string         `json:"tags"`
	Outcome       *string          `json:"outcome"`
	ClosedBy      *string          `json:"closedBy"`
}

type AddCaseNoteRequest struct {
	Content  string `json:"content"`
	Author   string `json:"author"`
	NoteType string `json:"noteType"`
}

type CreateSarReportRequest struct {
	ReportType        string           `json:"reportType"`
	SubjectCustomerID string           `json:"subjectCustomerId"`
	SubjectName       string           `json:"subjectName"`
	SubjectNationalID string           `json:"subjectNationalId"`
	Narrative         string           `json:"narrative"`
	SuspiciousAmount  *decimal.Decimal `json:"suspiciousAmount"`
	ActivityStartDate *time.Time       `json:"activityStartDate"`
	ActivityEndDate   *time.Time       `json:"activityEndDate"`
	AlertIDs          []uuid.UUID      `json:"alertIds"`
	CaseID            *uuid.UUID       `json:"caseId"`
	PreparedBy        string           `json:"preparedBy"`
}

type UpdateSarReportRequest struct {
	Status            *string          `json:"status"`
	Narrative         *string          `json:"narrative"`
	SuspiciousAmount  *decimal.Decimal `json:"suspiciousAmount"`
	ActivityStartDate *time.Time       `json:"activityStartDate"`
	ActivityEndDate   *time.Time       `json:"activityEndDate"`
	AlertIDs          []uuid.UUID      `json:"alertIds"`
	ReviewedBy        *string          `json:"reviewedBy"`
	FiledBy           *string          `json:"filedBy"`
	FilingReference   *string          `json:"filingReference"`
}

type CreateWatchlistEntryRequest struct {
	ListType   string     `json:"listType"`
	EntryType  string     `json:"entryType"`
	Name       string     `json:"name"`
	NationalID string     `json:"nationalId"`
	Phone      string     `json:"phone"`
	Reason     string     `json:"reason"`
	Source     string     `json:"source"`
	ExpiresAt  *time.Time `json:"expiresAt"`
}

type ScreenCustomerRequest struct {
	CustomerID string `json:"customerId"`
	Name       string `json:"name"`
	NationalID string `json:"nationalId"`
	Phone      string `json:"phone"`
}

type ScoreTransactionRequest struct {
	CustomerID string  `json:"customerId"`
	EventType  string  `json:"eventType"`
	Amount     *decimal.Decimal `json:"amount"`
	RuleScore  float64 `json:"ruleScore"`
}

// ─── Response DTOs ──────────────────────────────────────────────────────────

type FraudSummaryResponse struct {
	TenantID              string `json:"tenantId"`
	OpenAlerts            int64  `json:"openAlerts"`
	UnderReviewAlerts     int64  `json:"underReviewAlerts"`
	EscalatedAlerts       int64  `json:"escalatedAlerts"`
	ConfirmedFraud        int64  `json:"confirmedFraud"`
	CriticalAlerts        int64  `json:"criticalAlerts"`
	HighRiskCustomers     int64  `json:"highRiskCustomers"`
	CriticalRiskCustomers int64  `json:"criticalRiskCustomers"`
}

type BatchScreeningResult struct {
	CustomersScreened  int      `json:"customersScreened"`
	MatchesFound       int      `json:"matchesFound"`
	AlertsCreated      int      `json:"alertsCreated"`
	MatchedCustomerIDs []string `json:"matchedCustomerIds"`
}

type CaseTimelineResponse struct {
	CaseID     uuid.UUID       `json:"caseId"`
	CaseNumber string          `json:"caseNumber"`
	Events     []TimelineEvent `json:"events"`
}

type TimelineEvent struct {
	Action      string    `json:"action"`
	Description string    `json:"description"`
	PerformedBy string    `json:"performedBy"`
	Timestamp   time.Time `json:"timestamp"`
}

type NetworkNodeResponse struct {
	CustomerID string         `json:"customerId"`
	RiskLevel  string         `json:"riskLevel"`
	LinkCount  int            `json:"linkCount"`
	Links      []LinkResponse `json:"links"`
}

type LinkResponse struct {
	LinkedCustomerID string `json:"linkedCustomerId"`
	LinkType         string `json:"linkType"`
	LinkValue        string `json:"linkValue"`
	Strength         int    `json:"strength"`
	Flagged          bool   `json:"flagged"`
}

type FraudAnalyticsResponse struct {
	TotalAlerts        int64               `json:"totalAlerts"`
	ResolvedAlerts     int64               `json:"resolvedAlerts"`
	ResolutionRate     float64             `json:"resolutionRate"`
	ActiveCases        int64               `json:"activeCases"`
	ConfirmedFraudCount int64              `json:"confirmedFraudCount"`
	FalsePositiveCount int64               `json:"falsePositiveCount"`
	PrecisionRate      float64             `json:"precisionRate"`
	RuleEffectiveness  []RuleEffectiveness `json:"ruleEffectiveness"`
	DailyTrend         []DailyAlertCount   `json:"dailyTrend"`
	AlertsByType       []TypeCount         `json:"alertsByType"`
}

type RuleEffectiveness struct {
	RuleCode       string  `json:"ruleCode"`
	TotalTriggers  int64   `json:"totalTriggers"`
	ConfirmedFraud int64   `json:"confirmedFraud"`
	FalsePositives int64   `json:"falsePositives"`
	PrecisionRate  float64 `json:"precisionRate"`
}

type DailyAlertCount struct {
	Date  string `json:"date"`
	Count int64  `json:"count"`
}

type TypeCount struct {
	Type  string `json:"type"`
	Count int64  `json:"count"`
}

// ─── Threshold Config ───────────────────────────────────────────────────────

type ThresholdConfig struct {
	LargeTransactionAmount    decimal.Decimal
	StructuringWindowHours    int
	StructuringThreshold      decimal.Decimal
	VelocityMaxTransactions1h int
	VelocityMaxTransactions24h int
	VelocityMaxApplications30d int
	RapidTransferWindowMinutes int
	DormantAccountDays        int
	EarlyPayoffDays           int
	LoanCyclingWindowDays     int
}

func DefaultThresholdConfig() ThresholdConfig {
	return ThresholdConfig{
		LargeTransactionAmount:     decimal.NewFromInt(1000000),
		StructuringWindowHours:     24,
		StructuringThreshold:       decimal.NewFromInt(1000000),
		VelocityMaxTransactions1h:  10,
		VelocityMaxTransactions24h: 50,
		VelocityMaxApplications30d: 5,
		RapidTransferWindowMinutes: 15,
		DormantAccountDays:         180,
		EarlyPayoffDays:            30,
		LoanCyclingWindowDays:      7,
	}
}
