package model

import (
	"math"
	"time"

	"github.com/shopspring/decimal"
)

// ScoringStatus represents the status of a scoring request.
type ScoringStatus string

const (
	ScoringStatusPending    ScoringStatus = "PENDING"
	ScoringStatusInProgress ScoringStatus = "IN_PROGRESS"
	ScoringStatusCompleted  ScoringStatus = "COMPLETED"
	ScoringStatusFailed     ScoringStatus = "FAILED"
	ScoringStatusSkipped    ScoringStatus = "SKIPPED"
)

// ScoreBand represents credit score quality bands.
type ScoreBand string

const (
	ScoreBandExcellent ScoreBand = "EXCELLENT"
	ScoreBandGood      ScoreBand = "GOOD"
	ScoreBandFair      ScoreBand = "FAIR"
	ScoreBandMarginal  ScoreBand = "MARGINAL"
	ScoreBandPoor      ScoreBand = "POOR"
)

// ScoreBandLabel returns the score range label for a band.
func ScoreBandLabel(band ScoreBand) string {
	switch band {
	case ScoreBandExcellent:
		return "750-850"
	case ScoreBandGood:
		return "670-749"
	case ScoreBandFair:
		return "580-669"
	case ScoreBandMarginal:
		return "500-579"
	case ScoreBandPoor:
		return "300-499"
	default:
		return "300-499"
	}
}

// ScoreBandFromString parses a string into a ScoreBand, defaulting to POOR.
func ScoreBandFromString(band string) ScoreBand {
	switch band {
	case "EXCELLENT":
		return ScoreBandExcellent
	case "GOOD":
		return ScoreBandGood
	case "FAIR":
		return ScoreBandFair
	case "MARGINAL":
		return ScoreBandMarginal
	case "POOR":
		return ScoreBandPoor
	default:
		return ScoreBandPoor
	}
}

// ScoringRequest represents a scoring request entity.
type ScoringRequest struct {
	ID                string         `json:"id"`
	TenantID          string         `json:"tenantId"`
	LoanApplicationID string         `json:"loanApplicationId"`
	CustomerID        int64          `json:"customerId"`
	Status            ScoringStatus  `json:"status"`
	TriggerEvent      string         `json:"triggerEvent,omitempty"`
	RequestedAt       time.Time      `json:"requestedAt"`
	CompletedAt       *time.Time     `json:"completedAt,omitempty"`
	ErrorMessage      string         `json:"errorMessage,omitempty"`
	CreatedAt         time.Time      `json:"createdAt"`
	UpdatedAt         time.Time      `json:"updatedAt"`
}

// ScoringResult represents a scoring result entity.
type ScoringResult struct {
	ID                string          `json:"id"`
	TenantID          string          `json:"tenantId"`
	RequestID         string          `json:"requestId"`
	LoanApplicationID string          `json:"loanApplicationId"`
	CustomerID        int64           `json:"customerId"`
	BaseScore         decimal.Decimal `json:"baseScore"`
	CrbContribution   decimal.Decimal `json:"crbContribution"`
	LlmAdjustment     decimal.Decimal `json:"llmAdjustment"`
	PdProbability     decimal.Decimal `json:"pdProbability"`
	FinalScore        decimal.Decimal `json:"finalScore"`
	ScoreBand         string          `json:"scoreBand"`
	Reasoning         string          `json:"reasoning,omitempty"`
	LlmProvider       string          `json:"llmProvider,omitempty"`
	LlmModel          string          `json:"llmModel,omitempty"`
	RawResponse       string          `json:"rawResponse,omitempty"`
	ScoredAt          *time.Time      `json:"scoredAt,omitempty"`
	CreatedAt         time.Time       `json:"createdAt"`
}

// ScoringRequestResponse is the API response DTO for a scoring request.
type ScoringRequestResponse struct {
	ID                string        `json:"id"`
	TenantID          string        `json:"tenantId"`
	LoanApplicationID string        `json:"loanApplicationId"`
	CustomerID        int64         `json:"customerId"`
	Status            ScoringStatus `json:"status"`
	TriggerEvent      string        `json:"triggerEvent,omitempty"`
	RequestedAt       time.Time     `json:"requestedAt"`
	CompletedAt       *time.Time    `json:"completedAt,omitempty"`
	ErrorMessage      string        `json:"errorMessage,omitempty"`
	CreatedAt         time.Time     `json:"createdAt"`
}

// ScoringResultResponse is the API response DTO for a scoring result.
type ScoringResultResponse struct {
	ID                string          `json:"id"`
	RequestID         string          `json:"requestId"`
	LoanApplicationID string          `json:"loanApplicationId"`
	CustomerID        int64           `json:"customerId"`
	BaseScore         decimal.Decimal `json:"baseScore"`
	CrbContribution   decimal.Decimal `json:"crbContribution"`
	LlmAdjustment     decimal.Decimal `json:"llmAdjustment"`
	PdProbability     decimal.Decimal `json:"pdProbability"`
	FinalScore        decimal.Decimal `json:"finalScore"`
	ScoreBand         string          `json:"scoreBand"`
	Reasoning         []string        `json:"reasoning"`
	LlmProvider       string          `json:"llmProvider,omitempty"`
	LlmModel          string          `json:"llmModel,omitempty"`
	ScoredAt          *time.Time      `json:"scoredAt,omitempty"`
	CreatedAt         time.Time       `json:"createdAt"`
}

// ManualScoringRequest is the API request DTO for triggering a manual score.
type ManualScoringRequest struct {
	LoanApplicationID string `json:"loanApplicationId"`
	CustomerID        int64  `json:"customerId"`
	TriggerEvent      string `json:"triggerEvent,omitempty"`
}

// ExternalScoreResponse is the response from the external credit scoring API.
type ExternalScoreResponse struct {
	CustomerID      int64           `json:"customer_id"`
	BaseScore       decimal.Decimal `json:"base_score"`
	CrbContribution decimal.Decimal `json:"crb_contribution"`
	LlmAdjustment   decimal.Decimal `json:"llm_adjustment"`
	PdProbability   decimal.Decimal `json:"pd_probability"`
	FinalScore      decimal.Decimal `json:"final_score"`
	ScoreBand       string          `json:"score_band"`
	Reasoning       []string        `json:"reasoning"`
	LlmProvider     string          `json:"llm_provider"`
	LlmModel        string          `json:"llm_model"`
	ScoredAt        string          `json:"scored_at"`
}

// FlexibleCustomerID parses a customer ID from a string.
// Accepts numeric values directly; non-numeric strings are hashed to a positive int64.
func FlexibleCustomerID(raw string) int64 {
	if raw == "" {
		return 0
	}
	// Try parse as int64
	var n int64
	for _, c := range raw {
		if c >= '0' && c <= '9' {
			n = n*10 + int64(c-'0')
		} else {
			// Non-numeric: hash it
			h := int64(0)
			for _, ch := range raw {
				h = h*31 + int64(ch)
			}
			return int64(math.Abs(float64(h)))
		}
	}
	return n
}
