package model

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// ---------- Enums ----------

type FloatAccountStatus string

const (
	FloatAccountStatusActive     FloatAccountStatus = "ACTIVE"
	FloatAccountStatusRestricted FloatAccountStatus = "RESTRICTED"
	FloatAccountStatusClosed     FloatAccountStatus = "CLOSED"
)

type FloatAllocationStatus string

const (
	FloatAllocationStatusActive     FloatAllocationStatus = "ACTIVE"
	FloatAllocationStatusClosed     FloatAllocationStatus = "CLOSED"
	FloatAllocationStatusWrittenOff FloatAllocationStatus = "WRITTEN_OFF"
)

type FloatTransactionType string

const (
	FloatTransactionTypeDraw       FloatTransactionType = "DRAW"
	FloatTransactionTypeRepayment  FloatTransactionType = "REPAYMENT"
	FloatTransactionTypeTopUp      FloatTransactionType = "TOP_UP"
	FloatTransactionTypeFee        FloatTransactionType = "FEE"
	FloatTransactionTypeAdjustment FloatTransactionType = "ADJUSTMENT"
)

// ---------- Entities ----------

// FloatAccount represents a float facility account.
type FloatAccount struct {
	ID          uuid.UUID          `json:"id"`
	TenantID    string             `json:"tenantId"`
	AccountName string             `json:"accountName"`
	AccountCode string             `json:"accountCode"`
	Currency    string             `json:"currency"`
	FloatLimit  decimal.Decimal    `json:"floatLimit"`
	DrawnAmount decimal.Decimal    `json:"drawnAmount"`
	Available   decimal.Decimal    `json:"available"` // computed: floatLimit - drawnAmount
	Status      FloatAccountStatus `json:"status"`
	Description string             `json:"description,omitempty"`
	CreatedAt   time.Time          `json:"createdAt"`
	UpdatedAt   time.Time          `json:"updatedAt"`
}

// FloatTransaction records a single draw, repayment, or top-up on a float account.
type FloatTransaction struct {
	ID             uuid.UUID            `json:"id"`
	TenantID       string               `json:"tenantId"`
	FloatAccountID uuid.UUID            `json:"floatAccountId"`
	TransactionType FloatTransactionType `json:"transactionType"`
	Amount         decimal.Decimal      `json:"amount"`
	BalanceBefore  decimal.Decimal      `json:"balanceBefore"`
	BalanceAfter   decimal.Decimal      `json:"balanceAfter"`
	ReferenceID    string               `json:"referenceId,omitempty"`
	ReferenceType  string               `json:"referenceType,omitempty"`
	Narration      string               `json:"narration,omitempty"`
	EventID        string               `json:"eventId,omitempty"`
	CreatedAt      time.Time            `json:"createdAt"`
}

// FloatAllocation tracks float drawn per loan.
type FloatAllocation struct {
	ID             uuid.UUID             `json:"id"`
	TenantID       string                `json:"tenantId"`
	FloatAccountID uuid.UUID             `json:"floatAccountId"`
	LoanID         uuid.UUID             `json:"loanId"`
	AllocatedAmount decimal.Decimal      `json:"allocatedAmount"`
	RepaidAmount   decimal.Decimal       `json:"repaidAmount"`
	Outstanding    decimal.Decimal       `json:"outstanding"` // computed: allocatedAmount - repaidAmount
	Status         FloatAllocationStatus `json:"status"`
	DisbursedAt    time.Time             `json:"disbursedAt"`
	ClosedAt       *time.Time            `json:"closedAt,omitempty"`
	CreatedAt      time.Time             `json:"createdAt"`
}

// ---------- Request DTOs ----------

// CreateFloatAccountRequest is the request body for creating a float account.
type CreateFloatAccountRequest struct {
	AccountName string          `json:"accountName"`
	AccountCode string          `json:"accountCode"`
	Currency    string          `json:"currency"`
	FloatLimit  decimal.Decimal `json:"floatLimit"`
	Description string          `json:"description,omitempty"`
}

// Validate validates the create request.
func (r *CreateFloatAccountRequest) Validate() string {
	if r.AccountName == "" {
		return "accountName is required"
	}
	if r.AccountCode == "" {
		return "accountCode is required"
	}
	if r.FloatLimit.IsNegative() {
		return "floatLimit must be >= 0"
	}
	return ""
}

// FloatDrawRequest is the request body for drawing from a float account.
type FloatDrawRequest struct {
	Amount        decimal.Decimal `json:"amount"`
	ReferenceID   string          `json:"referenceId,omitempty"`
	ReferenceType string          `json:"referenceType,omitempty"`
	Narration     string          `json:"narration,omitempty"`
}

// Validate validates the draw request.
func (r *FloatDrawRequest) Validate() string {
	if r.Amount.LessThanOrEqual(decimal.Zero) {
		return "amount must be > 0"
	}
	return ""
}

// FloatRepayRequest is the request body for repaying a float account.
type FloatRepayRequest struct {
	Amount      decimal.Decimal `json:"amount"`
	ReferenceID string          `json:"referenceId,omitempty"`
	Narration   string          `json:"narration,omitempty"`
}

// Validate validates the repay request.
func (r *FloatRepayRequest) Validate() string {
	if r.Amount.LessThanOrEqual(decimal.Zero) {
		return "amount must be > 0"
	}
	return ""
}

// ---------- Response DTOs ----------

// FloatAccountResponse is the API response for a float account.
type FloatAccountResponse struct {
	ID          uuid.UUID          `json:"id"`
	TenantID    string             `json:"tenantId"`
	AccountName string             `json:"accountName"`
	AccountCode string             `json:"accountCode"`
	Currency    string             `json:"currency"`
	FloatLimit  decimal.Decimal    `json:"floatLimit"`
	DrawnAmount decimal.Decimal    `json:"drawnAmount"`
	Available   decimal.Decimal    `json:"available"`
	Status      FloatAccountStatus `json:"status"`
	Description string             `json:"description,omitempty"`
	CreatedAt   time.Time          `json:"createdAt"`
	UpdatedAt   time.Time          `json:"updatedAt"`
}

// FloatTransactionResponse is the API response for a float transaction.
type FloatTransactionResponse struct {
	ID              uuid.UUID            `json:"id"`
	FloatAccountID  uuid.UUID            `json:"floatAccountId"`
	TransactionType FloatTransactionType `json:"transactionType"`
	Amount          decimal.Decimal      `json:"amount"`
	BalanceBefore   decimal.Decimal      `json:"balanceBefore"`
	BalanceAfter    decimal.Decimal      `json:"balanceAfter"`
	ReferenceID     string               `json:"referenceId,omitempty"`
	ReferenceType   string               `json:"referenceType,omitempty"`
	Narration       string               `json:"narration,omitempty"`
	CreatedAt       time.Time            `json:"createdAt"`
}

// FloatAllocationResponse is the API response for a float allocation.
type FloatAllocationResponse struct {
	ID              uuid.UUID             `json:"id"`
	FloatAccountID  uuid.UUID             `json:"floatAccountId"`
	LoanID          uuid.UUID             `json:"loanId"`
	AllocatedAmount decimal.Decimal       `json:"allocatedAmount"`
	RepaidAmount    decimal.Decimal       `json:"repaidAmount"`
	Outstanding     decimal.Decimal       `json:"outstanding"`
	Status          FloatAllocationStatus `json:"status"`
	DisbursedAt     time.Time             `json:"disbursedAt"`
	ClosedAt        *time.Time            `json:"closedAt,omitempty"`
	CreatedAt       time.Time             `json:"createdAt"`
}

// FloatSummaryResponse is the API response for the float summary.
type FloatSummaryResponse struct {
	TenantID          string          `json:"tenantId"`
	TotalLimit        decimal.Decimal `json:"totalLimit"`
	TotalDrawn        decimal.Decimal `json:"totalDrawn"`
	TotalAvailable    decimal.Decimal `json:"totalAvailable"`
	ActiveAccounts    int             `json:"activeAccounts"`
	ActiveAllocations int             `json:"activeAllocations"`
}

// ---------- Helpers ----------

// ToAccountResponse converts a FloatAccount entity to its response DTO.
func ToAccountResponse(a *FloatAccount) FloatAccountResponse {
	return FloatAccountResponse{
		ID:          a.ID,
		TenantID:    a.TenantID,
		AccountName: a.AccountName,
		AccountCode: a.AccountCode,
		Currency:    a.Currency,
		FloatLimit:  a.FloatLimit,
		DrawnAmount: a.DrawnAmount,
		Available:   a.FloatLimit.Sub(a.DrawnAmount),
		Status:      a.Status,
		Description: a.Description,
		CreatedAt:   a.CreatedAt,
		UpdatedAt:   a.UpdatedAt,
	}
}

// ToTransactionResponse converts a FloatTransaction entity to its response DTO.
func ToTransactionResponse(t *FloatTransaction) FloatTransactionResponse {
	return FloatTransactionResponse{
		ID:              t.ID,
		FloatAccountID:  t.FloatAccountID,
		TransactionType: t.TransactionType,
		Amount:          t.Amount,
		BalanceBefore:   t.BalanceBefore,
		BalanceAfter:    t.BalanceAfter,
		ReferenceID:     t.ReferenceID,
		ReferenceType:   t.ReferenceType,
		Narration:       t.Narration,
		CreatedAt:       t.CreatedAt,
	}
}
