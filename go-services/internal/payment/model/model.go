package model

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// PaymentStatus represents the lifecycle state of a payment.
type PaymentStatus string

const (
	PaymentStatusPending    PaymentStatus = "PENDING"
	PaymentStatusProcessing PaymentStatus = "PROCESSING"
	PaymentStatusCompleted  PaymentStatus = "COMPLETED"
	PaymentStatusFailed     PaymentStatus = "FAILED"
	PaymentStatusReversed   PaymentStatus = "REVERSED"
)

// ValidPaymentStatuses lists all valid payment statuses.
var ValidPaymentStatuses = map[PaymentStatus]bool{
	PaymentStatusPending:    true,
	PaymentStatusProcessing: true,
	PaymentStatusCompleted:  true,
	PaymentStatusFailed:     true,
	PaymentStatusReversed:   true,
}

// PaymentType represents the purpose of a payment.
type PaymentType string

const (
	PaymentTypeLoanDisbursement PaymentType = "LOAN_DISBURSEMENT"
	PaymentTypeLoanRepayment    PaymentType = "LOAN_REPAYMENT"
	PaymentTypeFee              PaymentType = "FEE"
	PaymentTypePenalty          PaymentType = "PENALTY"
	PaymentTypeFloatTransfer    PaymentType = "FLOAT_TRANSFER"
	PaymentTypeOther            PaymentType = "OTHER"
)

// ValidPaymentTypes lists all valid payment types.
var ValidPaymentTypes = map[PaymentType]bool{
	PaymentTypeLoanDisbursement: true,
	PaymentTypeLoanRepayment:    true,
	PaymentTypeFee:              true,
	PaymentTypePenalty:          true,
	PaymentTypeFloatTransfer:    true,
	PaymentTypeOther:            true,
}

// PaymentChannel represents the channel through which a payment is made.
type PaymentChannel string

const (
	PaymentChannelMpesa        PaymentChannel = "MPESA"
	PaymentChannelBankTransfer PaymentChannel = "BANK_TRANSFER"
	PaymentChannelCard         PaymentChannel = "CARD"
	PaymentChannelCash         PaymentChannel = "CASH"
	PaymentChannelInternal     PaymentChannel = "INTERNAL"
)

// ValidPaymentChannels lists all valid payment channels.
var ValidPaymentChannels = map[PaymentChannel]bool{
	PaymentChannelMpesa:        true,
	PaymentChannelBankTransfer: true,
	PaymentChannelCard:         true,
	PaymentChannelCash:         true,
	PaymentChannelInternal:     true,
}

// PaymentMethodType represents the type of saved payment method.
type PaymentMethodType string

const (
	PaymentMethodTypeMpesa       PaymentMethodType = "MPESA"
	PaymentMethodTypeBankAccount PaymentMethodType = "BANK_ACCOUNT"
	PaymentMethodTypeCard        PaymentMethodType = "CARD"
)

// ValidPaymentMethodTypes lists all valid payment method types.
var ValidPaymentMethodTypes = map[PaymentMethodType]bool{
	PaymentMethodTypeMpesa:       true,
	PaymentMethodTypeBankAccount: true,
	PaymentMethodTypeCard:        true,
}

// Payment represents a payment record in the system.
type Payment struct {
	ID                uuid.UUID       `json:"id"`
	TenantID          string          `json:"tenantId"`
	CustomerID        string          `json:"customerId"`
	LoanID            *uuid.UUID      `json:"loanId,omitempty"`
	ApplicationID     *uuid.UUID      `json:"applicationId,omitempty"`
	PaymentType       PaymentType     `json:"paymentType"`
	PaymentChannel    PaymentChannel  `json:"paymentChannel"`
	Status            PaymentStatus   `json:"status"`
	Amount            decimal.Decimal `json:"amount"`
	Currency          string          `json:"currency"`
	ExternalReference *string         `json:"externalReference,omitempty"`
	InternalReference string          `json:"internalReference"`
	Description       *string         `json:"description,omitempty"`
	FailureReason     *string         `json:"failureReason,omitempty"`
	ReversalReason    *string         `json:"reversalReason,omitempty"`
	PaymentMethodID   *uuid.UUID      `json:"paymentMethodId,omitempty"`
	InitiatedAt       time.Time       `json:"initiatedAt"`
	ProcessedAt       *time.Time      `json:"processedAt,omitempty"`
	CompletedAt       *time.Time      `json:"completedAt,omitempty"`
	ReversedAt        *time.Time      `json:"reversedAt,omitempty"`
	CreatedAt         time.Time       `json:"createdAt"`
	UpdatedAt         time.Time       `json:"updatedAt"`
	CreatedBy         *string         `json:"createdBy,omitempty"`
}

// PaymentMethod represents a saved payment method for a customer.
type PaymentMethod struct {
	ID            uuid.UUID         `json:"id"`
	TenantID      string            `json:"tenantId"`
	CustomerID    string            `json:"customerId"`
	MethodType    PaymentMethodType `json:"methodType"`
	Alias         *string           `json:"alias,omitempty"`
	AccountNumber string            `json:"accountNumber"`
	AccountName   *string           `json:"accountName,omitempty"`
	Provider      *string           `json:"provider,omitempty"`
	IsDefault     bool              `json:"isDefault"`
	IsActive      bool              `json:"isActive"`
	CreatedAt     time.Time         `json:"createdAt"`
	UpdatedAt     time.Time         `json:"updatedAt"`
}

// InitiatePaymentRequest is the DTO for creating a new payment.
type InitiatePaymentRequest struct {
	CustomerID      string          `json:"customerId"`
	PaymentType     PaymentType     `json:"paymentType"`
	PaymentChannel  PaymentChannel  `json:"paymentChannel"`
	Amount          decimal.Decimal `json:"amount"`
	Currency        string          `json:"currency"`
	LoanID          *uuid.UUID      `json:"loanId,omitempty"`
	ApplicationID   *uuid.UUID      `json:"applicationId,omitempty"`
	PaymentMethodID *uuid.UUID      `json:"paymentMethodId,omitempty"`
	ExternalReference *string       `json:"externalReference,omitempty"`
	Description     *string         `json:"description,omitempty"`
}

// CompletePaymentRequest is the DTO for completing a payment.
type CompletePaymentRequest struct {
	ExternalReference *string `json:"externalReference,omitempty"`
	Notes             *string `json:"notes,omitempty"`
}

// FailPaymentRequest is the DTO for failing a payment.
type FailPaymentRequest struct {
	Reason string `json:"reason"`
}

// ReversePaymentRequest is the DTO for reversing a payment.
type ReversePaymentRequest struct {
	Reason string `json:"reason"`
}

// AddPaymentMethodRequest is the DTO for adding a payment method.
type AddPaymentMethodRequest struct {
	CustomerID    string            `json:"customerId"`
	MethodType    PaymentMethodType `json:"methodType"`
	AccountNumber string            `json:"accountNumber"`
	AccountName   *string           `json:"accountName,omitempty"`
	Alias         *string           `json:"alias,omitempty"`
	Provider      *string           `json:"provider,omitempty"`
	IsDefault     bool              `json:"isDefault"`
}

// PaymentResponse is the DTO returned for payment queries.
type PaymentResponse struct {
	ID                uuid.UUID       `json:"id"`
	TenantID          string          `json:"tenantId"`
	CustomerID        string          `json:"customerId"`
	LoanID            *uuid.UUID      `json:"loanId,omitempty"`
	ApplicationID     *uuid.UUID      `json:"applicationId,omitempty"`
	PaymentType       PaymentType     `json:"paymentType"`
	PaymentChannel    PaymentChannel  `json:"paymentChannel"`
	Status            PaymentStatus   `json:"status"`
	Amount            decimal.Decimal `json:"amount"`
	Currency          string          `json:"currency"`
	ExternalReference *string         `json:"externalReference,omitempty"`
	InternalReference string          `json:"internalReference"`
	Description       *string         `json:"description,omitempty"`
	FailureReason     *string         `json:"failureReason,omitempty"`
	ReversalReason    *string         `json:"reversalReason,omitempty"`
	InitiatedAt       time.Time       `json:"initiatedAt"`
	ProcessedAt       *time.Time      `json:"processedAt,omitempty"`
	CompletedAt       *time.Time      `json:"completedAt,omitempty"`
	ReversedAt        *time.Time      `json:"reversedAt,omitempty"`
	CreatedAt         time.Time       `json:"createdAt"`
}

// PaymentMethodResponse is the DTO returned for payment method queries.
type PaymentMethodResponse struct {
	ID            uuid.UUID         `json:"id"`
	CustomerID    string            `json:"customerId"`
	MethodType    PaymentMethodType `json:"methodType"`
	Alias         *string           `json:"alias,omitempty"`
	AccountNumber string            `json:"accountNumber"`
	AccountName   *string           `json:"accountName,omitempty"`
	Provider      *string           `json:"provider,omitempty"`
	IsDefault     bool              `json:"isDefault"`
	IsActive      bool              `json:"isActive"`
	CreatedAt     time.Time         `json:"createdAt"`
}
