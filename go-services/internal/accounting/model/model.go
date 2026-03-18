package model

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// AccountType represents the type of GL account.
type AccountType string

const (
	AccountTypeAsset     AccountType = "ASSET"
	AccountTypeLiability AccountType = "LIABILITY"
	AccountTypeEquity    AccountType = "EQUITY"
	AccountTypeIncome    AccountType = "INCOME"
	AccountTypeExpense   AccountType = "EXPENSE"
)

// ValidAccountTypes lists all valid account types.
var ValidAccountTypes = map[AccountType]bool{
	AccountTypeAsset:     true,
	AccountTypeLiability: true,
	AccountTypeEquity:    true,
	AccountTypeIncome:    true,
	AccountTypeExpense:   true,
}

// BalanceType indicates whether an account normally has a debit or credit balance.
type BalanceType string

const (
	BalanceTypeDebit  BalanceType = "DEBIT"
	BalanceTypeCredit BalanceType = "CREDIT"
)

// ValidBalanceTypes lists all valid balance types.
var ValidBalanceTypes = map[BalanceType]bool{
	BalanceTypeDebit:  true,
	BalanceTypeCredit: true,
}

// EntryStatus represents the lifecycle state of a journal entry.
type EntryStatus string

const (
	EntryStatusDraft    EntryStatus = "DRAFT"
	EntryStatusPosted   EntryStatus = "POSTED"
	EntryStatusReversed EntryStatus = "REVERSED"
)

// ChartOfAccount represents a GL account in the chart of accounts.
type ChartOfAccount struct {
	ID          uuid.UUID   `json:"id"`
	TenantID    string      `json:"tenantId"`
	Code        string      `json:"code"`
	Name        string      `json:"name"`
	AccountType AccountType `json:"accountType"`
	BalanceType BalanceType `json:"balanceType"`
	ParentID    *uuid.UUID  `json:"parentId,omitempty"`
	Description *string     `json:"description,omitempty"`
	IsActive    bool        `json:"isActive"`
	CreatedAt   time.Time   `json:"createdAt"`
	UpdatedAt   time.Time   `json:"updatedAt"`
}

// JournalEntry represents a double-entry journal entry header.
type JournalEntry struct {
	ID          uuid.UUID       `json:"id"`
	TenantID    string          `json:"tenantId"`
	Reference   string          `json:"reference"`
	Description *string         `json:"description,omitempty"`
	EntryDate   time.Time       `json:"entryDate"`
	Status      EntryStatus     `json:"status"`
	SourceEvent *string         `json:"sourceEvent,omitempty"`
	SourceID    *string         `json:"sourceId,omitempty"`
	TotalDebit  decimal.Decimal `json:"totalDebit"`
	TotalCredit decimal.Decimal `json:"totalCredit"`
	PostedBy    *string         `json:"postedBy,omitempty"`
	CreatedAt   time.Time       `json:"createdAt"`
	UpdatedAt   time.Time       `json:"updatedAt"`
	Lines       []JournalLine   `json:"lines,omitempty"`
}

// JournalLine represents a single debit or credit line in a journal entry.
type JournalLine struct {
	ID           uuid.UUID       `json:"id"`
	EntryID      uuid.UUID       `json:"entryId"`
	TenantID     string          `json:"tenantId"`
	AccountID    uuid.UUID       `json:"accountId"`
	LineNo       int             `json:"lineNo"`
	Description  *string         `json:"description,omitempty"`
	DebitAmount  decimal.Decimal `json:"debitAmount"`
	CreditAmount decimal.Decimal `json:"creditAmount"`
	Currency     string          `json:"currency"`
}

// AccountBalance tracks period-level balance summaries for each GL account.
type AccountBalance struct {
	ID             uuid.UUID       `json:"id"`
	TenantID       string          `json:"tenantId"`
	AccountID      uuid.UUID       `json:"accountId"`
	PeriodYear     int             `json:"periodYear"`
	PeriodMonth    int             `json:"periodMonth"`
	OpeningBalance decimal.Decimal `json:"openingBalance"`
	TotalDebits    decimal.Decimal `json:"totalDebits"`
	TotalCredits   decimal.Decimal `json:"totalCredits"`
	ClosingBalance decimal.Decimal `json:"closingBalance"`
	Currency       string          `json:"currency"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

// --- Request DTOs ---

// CreateAccountRequest is the request body for creating a GL account.
type CreateAccountRequest struct {
	Code        string      `json:"code"`
	Name        string      `json:"name"`
	AccountType AccountType `json:"accountType"`
	BalanceType BalanceType `json:"balanceType"`
	ParentID    *uuid.UUID  `json:"parentId,omitempty"`
	Description *string     `json:"description,omitempty"`
}

// JournalLineRequest is a single line in a post-journal-entry request.
type JournalLineRequest struct {
	AccountID    uuid.UUID       `json:"accountId"`
	Description  *string         `json:"description,omitempty"`
	DebitAmount  decimal.Decimal `json:"debitAmount"`
	CreditAmount decimal.Decimal `json:"creditAmount"`
	Currency     string          `json:"currency"`
}

// PostJournalEntryRequest is the request body for posting a journal entry.
type PostJournalEntryRequest struct {
	Reference   string                `json:"reference"`
	Description *string               `json:"description,omitempty"`
	EntryDate   *time.Time            `json:"entryDate,omitempty"`
	Lines       []JournalLineRequest  `json:"lines"`
}

// --- Response DTOs ---

// AccountResponse is the response for a GL account.
type AccountResponse struct {
	ID          uuid.UUID   `json:"id"`
	TenantID    string      `json:"tenantId"`
	Code        string      `json:"code"`
	Name        string      `json:"name"`
	AccountType AccountType `json:"accountType"`
	BalanceType BalanceType `json:"balanceType"`
	ParentID    *uuid.UUID  `json:"parentId,omitempty"`
	Description *string     `json:"description,omitempty"`
	IsActive    bool        `json:"isActive"`
	CreatedAt   time.Time   `json:"createdAt"`
}

// BalanceResponse is the response for an account balance.
type BalanceResponse struct {
	AccountID   uuid.UUID       `json:"accountId"`
	AccountCode string          `json:"accountCode"`
	AccountName string          `json:"accountName"`
	AccountType string          `json:"accountType"`
	BalanceType string          `json:"balanceType"`
	Balance     decimal.Decimal `json:"balance"`
	Currency    string          `json:"currency"`
	PeriodYear  int             `json:"periodYear"`
	PeriodMonth int             `json:"periodMonth"`
}

// JournalLineResponse is the response for a single journal line.
type JournalLineResponse struct {
	ID           uuid.UUID       `json:"id"`
	AccountID    uuid.UUID       `json:"accountId"`
	AccountCode  string          `json:"accountCode,omitempty"`
	AccountName  string          `json:"accountName,omitempty"`
	LineNo       int             `json:"lineNo"`
	Description  *string         `json:"description,omitempty"`
	DebitAmount  decimal.Decimal `json:"debitAmount"`
	CreditAmount decimal.Decimal `json:"creditAmount"`
	Currency     string          `json:"currency"`
}

// JournalEntryResponse is the response for a journal entry with its lines.
type JournalEntryResponse struct {
	ID          uuid.UUID              `json:"id"`
	TenantID    string                 `json:"tenantId"`
	Reference   string                 `json:"reference"`
	Description *string                `json:"description,omitempty"`
	EntryDate   string                 `json:"entryDate"`
	Status      EntryStatus            `json:"status"`
	SourceEvent *string                `json:"sourceEvent,omitempty"`
	SourceID    *string                `json:"sourceId,omitempty"`
	TotalDebit  decimal.Decimal        `json:"totalDebit"`
	TotalCredit decimal.Decimal        `json:"totalCredit"`
	PostedBy    *string                `json:"postedBy,omitempty"`
	CreatedAt   time.Time              `json:"createdAt"`
	Lines       []JournalLineResponse  `json:"lines"`
}

// TrialBalanceResponse is the response for a trial balance report.
type TrialBalanceResponse struct {
	PeriodYear   int               `json:"periodYear"`
	PeriodMonth  int               `json:"periodMonth"`
	Accounts     []BalanceResponse `json:"accounts"`
	TotalDebits  decimal.Decimal   `json:"totalDebits"`
	TotalCredits decimal.Decimal   `json:"totalCredits"`
	Balanced     bool              `json:"balanced"`
}

// ToAccountResponse maps a ChartOfAccount to AccountResponse.
func ToAccountResponse(a *ChartOfAccount) AccountResponse {
	return AccountResponse{
		ID:          a.ID,
		TenantID:    a.TenantID,
		Code:        a.Code,
		Name:        a.Name,
		AccountType: a.AccountType,
		BalanceType: a.BalanceType,
		ParentID:    a.ParentID,
		Description: a.Description,
		IsActive:    a.IsActive,
		CreatedAt:   a.CreatedAt,
	}
}

// ToJournalLineResponse maps a JournalLine to JournalLineResponse.
func ToJournalLineResponse(l *JournalLine) JournalLineResponse {
	return JournalLineResponse{
		ID:           l.ID,
		AccountID:    l.AccountID,
		LineNo:       l.LineNo,
		Description:  l.Description,
		DebitAmount:  l.DebitAmount,
		CreditAmount: l.CreditAmount,
		Currency:     l.Currency,
	}
}

// ToJournalEntryResponse maps a JournalEntry (with lines) to JournalEntryResponse.
func ToJournalEntryResponse(e *JournalEntry) JournalEntryResponse {
	lines := make([]JournalLineResponse, 0, len(e.Lines))
	for i := range e.Lines {
		lines = append(lines, ToJournalLineResponse(&e.Lines[i]))
	}
	return JournalEntryResponse{
		ID:          e.ID,
		TenantID:    e.TenantID,
		Reference:   e.Reference,
		Description: e.Description,
		EntryDate:   e.EntryDate.Format("2006-01-02"),
		Status:      e.Status,
		SourceEvent: e.SourceEvent,
		SourceID:    e.SourceID,
		TotalDebit:  e.TotalDebit,
		TotalCredit: e.TotalCredit,
		PostedBy:    e.PostedBy,
		CreatedAt:   e.CreatedAt,
		Lines:       lines,
	}
}
