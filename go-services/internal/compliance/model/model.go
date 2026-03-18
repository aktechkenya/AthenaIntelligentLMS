package model

import (
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
)

// ─── Enums ──────────────────────────────────────────────────────────────────

// AlertType represents the type of an AML alert.
type AlertType string

const (
	AlertTypeLargeTransaction AlertType = "LARGE_TRANSACTION"
	AlertTypeRapidTransactions AlertType = "RAPID_TRANSACTIONS"
	AlertTypeUnusualPattern   AlertType = "UNUSUAL_PATTERN"
	AlertTypeHighRiskCustomer AlertType = "HIGH_RISK_CUSTOMER"
	AlertTypeSanctionedEntity AlertType = "SANCTIONED_ENTITY"
	AlertTypeStructuring      AlertType = "STRUCTURING"
	AlertTypePEPMatch         AlertType = "PEP_MATCH"
	AlertTypeGeographicRisk   AlertType = "GEOGRAPHIC_RISK"
	AlertTypeOther            AlertType = "OTHER"
)

// ValidAlertType checks if the given string is a valid AlertType.
func ValidAlertType(s string) bool {
	switch AlertType(s) {
	case AlertTypeLargeTransaction, AlertTypeRapidTransactions, AlertTypeUnusualPattern,
		AlertTypeHighRiskCustomer, AlertTypeSanctionedEntity, AlertTypeStructuring,
		AlertTypePEPMatch, AlertTypeGeographicRisk, AlertTypeOther:
		return true
	}
	return false
}

// AlertSeverity represents the severity level of an AML alert.
type AlertSeverity string

const (
	AlertSeverityLow      AlertSeverity = "LOW"
	AlertSeverityMedium   AlertSeverity = "MEDIUM"
	AlertSeverityHigh     AlertSeverity = "HIGH"
	AlertSeverityCritical AlertSeverity = "CRITICAL"
)

// ValidAlertSeverity checks if the given string is a valid AlertSeverity.
func ValidAlertSeverity(s string) bool {
	switch AlertSeverity(s) {
	case AlertSeverityLow, AlertSeverityMedium, AlertSeverityHigh, AlertSeverityCritical:
		return true
	}
	return false
}

// AlertStatus represents the status of an AML alert.
type AlertStatus string

const (
	AlertStatusOpen              AlertStatus = "OPEN"
	AlertStatusUnderReview       AlertStatus = "UNDER_REVIEW"
	AlertStatusEscalated         AlertStatus = "ESCALATED"
	AlertStatusSARFiled          AlertStatus = "SAR_FILED"
	AlertStatusClosedFalsePositive AlertStatus = "CLOSED_FALSE_POSITIVE"
	AlertStatusClosedActioned    AlertStatus = "CLOSED_ACTIONED"
)

// ValidAlertStatus checks if the given string is a valid AlertStatus.
func ValidAlertStatus(s string) bool {
	switch AlertStatus(s) {
	case AlertStatusOpen, AlertStatusUnderReview, AlertStatusEscalated,
		AlertStatusSARFiled, AlertStatusClosedFalsePositive, AlertStatusClosedActioned:
		return true
	}
	return false
}

// KycStatus represents the status of a KYC record.
type KycStatus string

const (
	KycStatusPending    KycStatus = "PENDING"
	KycStatusInProgress KycStatus = "IN_PROGRESS"
	KycStatusPassed     KycStatus = "PASSED"
	KycStatusFailed     KycStatus = "FAILED"
	KycStatusExpired    KycStatus = "EXPIRED"
)

// ValidKycStatus checks if the given string is a valid KycStatus.
func ValidKycStatus(s string) bool {
	switch KycStatus(s) {
	case KycStatusPending, KycStatusInProgress, KycStatusPassed, KycStatusFailed, KycStatusExpired:
		return true
	}
	return false
}

// RiskLevel represents the risk level assigned to a KYC record.
type RiskLevel string

const (
	RiskLevelLow      RiskLevel = "LOW"
	RiskLevelMedium   RiskLevel = "MEDIUM"
	RiskLevelHigh     RiskLevel = "HIGH"
	RiskLevelVeryHigh RiskLevel = "VERY_HIGH"
)

// ValidRiskLevel checks if the given string is a valid RiskLevel.
func ValidRiskLevel(s string) bool {
	switch RiskLevel(s) {
	case RiskLevelLow, RiskLevelMedium, RiskLevelHigh, RiskLevelVeryHigh:
		return true
	}
	return false
}

// ─── Entities ───────────────────────────────────────────────────────────────

// AmlAlert represents a row in the aml_alerts table.
type AmlAlert struct {
	ID              uuid.UUID        `json:"id"`
	TenantID        string           `json:"tenantId"`
	AlertType       AlertType        `json:"alertType"`
	Severity        AlertSeverity    `json:"severity"`
	Status          AlertStatus      `json:"status"`
	SubjectType     string           `json:"subjectType"`
	SubjectID       string           `json:"subjectId"`
	CustomerID      *string          `json:"customerId,omitempty"`
	Description     string           `json:"description"`
	TriggerEvent    *string          `json:"triggerEvent,omitempty"`
	TriggerAmount   *pgtype.Numeric  `json:"triggerAmount,omitempty"`
	SarFiled        bool             `json:"sarFiled"`
	SarReference    *string          `json:"sarReference,omitempty"`
	AssignedTo      *string          `json:"assignedTo,omitempty"`
	ResolvedBy      *string          `json:"resolvedBy,omitempty"`
	ResolvedAt      *time.Time       `json:"resolvedAt,omitempty"`
	ResolutionNotes *string          `json:"resolutionNotes,omitempty"`
	CreatedAt       time.Time        `json:"createdAt"`
	UpdatedAt       time.Time        `json:"updatedAt"`
}

// KycRecord represents a row in the kyc_records table.
type KycRecord struct {
	ID            uuid.UUID  `json:"id"`
	TenantID      string     `json:"tenantId"`
	CustomerID    string     `json:"customerId"`
	Status        KycStatus  `json:"status"`
	CheckType     string     `json:"checkType"`
	NationalID    *string    `json:"nationalId,omitempty"`
	FullName      *string    `json:"fullName,omitempty"`
	Phone         *string    `json:"phone,omitempty"`
	RiskLevel     RiskLevel  `json:"riskLevel"`
	FailureReason *string    `json:"failureReason,omitempty"`
	CheckedBy     *string    `json:"checkedBy,omitempty"`
	CheckedAt     *time.Time `json:"checkedAt,omitempty"`
	ExpiresAt     *time.Time `json:"expiresAt,omitempty"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
}

// ComplianceEvent represents a row in the compliance_events table.
type ComplianceEvent struct {
	ID            uuid.UUID `json:"id"`
	TenantID      string    `json:"tenantId"`
	EventType     string    `json:"eventType"`
	SourceService *string   `json:"sourceService,omitempty"`
	SubjectID     *string   `json:"subjectId,omitempty"`
	Payload       *string   `json:"payload,omitempty"`
	CreatedAt     time.Time `json:"createdAt"`
}

// SarFiling represents a row in the sar_filings table.
type SarFiling struct {
	ID              uuid.UUID `json:"id"`
	TenantID        string    `json:"tenantId"`
	AlertID         uuid.UUID `json:"alertId"`
	ReferenceNumber string    `json:"referenceNumber"`
	FilingDate      string    `json:"filingDate"`
	Regulator       string    `json:"regulator"`
	Status          string    `json:"status"`
	SubmittedBy     *string   `json:"submittedBy,omitempty"`
	Notes           *string   `json:"notes,omitempty"`
	CreatedAt       time.Time `json:"createdAt"`
}

// ─── Request DTOs ───────────────────────────────────────────────────────────

// CreateAlertRequest is the request body for creating an AML alert.
type CreateAlertRequest struct {
	AlertType     AlertType        `json:"alertType"`
	Severity      *AlertSeverity   `json:"severity,omitempty"`
	SubjectType   string           `json:"subjectType"`
	SubjectID     string           `json:"subjectId"`
	CustomerID    *string          `json:"customerId,omitempty"`
	Description   string           `json:"description"`
	TriggerEvent  *string          `json:"triggerEvent,omitempty"`
	TriggerAmount *pgtype.Numeric  `json:"triggerAmount,omitempty"`
}

// ResolveAlertRequest is the request body for resolving an AML alert.
type ResolveAlertRequest struct {
	ResolvedBy      string `json:"resolvedBy"`
	ResolutionNotes string `json:"resolutionNotes"`
}

// FileSarRequest is the request body for filing a SAR.
type FileSarRequest struct {
	ReferenceNumber string  `json:"referenceNumber"`
	FilingDate      *string `json:"filingDate,omitempty"`
	Regulator       *string `json:"regulator,omitempty"`
	SubmittedBy     *string `json:"submittedBy,omitempty"`
	Notes           *string `json:"notes,omitempty"`
}

// KycRequest is the request body for creating or updating a KYC record.
type KycRequest struct {
	CustomerID string     `json:"customerId"`
	CheckType  *string    `json:"checkType,omitempty"`
	NationalID *string    `json:"nationalId,omitempty"`
	FullName   *string    `json:"fullName,omitempty"`
	Phone      *string    `json:"phone,omitempty"`
	RiskLevel  *RiskLevel `json:"riskLevel,omitempty"`
}

// ─── Response DTOs ──────────────────────────────────────────────────────────

// ComplianceSummaryResponse is the response body for the compliance summary.
type ComplianceSummaryResponse struct {
	TenantID          string `json:"tenantId"`
	OpenAlerts        int64  `json:"openAlerts"`
	CriticalAlerts    int64  `json:"criticalAlerts"`
	UnderReviewAlerts int64  `json:"underReviewAlerts"`
	SarFiledAlerts    int64  `json:"sarFiledAlerts"`
	PendingKyc        int64  `json:"pendingKyc"`
	FailedKyc         int64  `json:"failedKyc"`
}
