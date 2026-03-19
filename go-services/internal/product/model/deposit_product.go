package model

import (
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// ─── Deposit Product Enums ──────────────────────────────────────────────────

type DepositProductCategory string

const (
	DepositCategorySavings      DepositProductCategory = "SAVINGS"
	DepositCategoryCurrent      DepositProductCategory = "CURRENT"
	DepositCategoryFixedDeposit DepositProductCategory = "FIXED_DEPOSIT"
	DepositCategoryCallDeposit  DepositProductCategory = "CALL_DEPOSIT"
	DepositCategoryWallet       DepositProductCategory = "WALLET"
)

func ParseDepositProductCategory(s string) (DepositProductCategory, error) {
	switch DepositProductCategory(strings.ToUpper(s)) {
	case DepositCategorySavings, DepositCategoryCurrent, DepositCategoryFixedDeposit,
		DepositCategoryCallDeposit, DepositCategoryWallet:
		return DepositProductCategory(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid deposit product category: %s", s)
	}
}

type DepositProductStatus string

const (
	DepositStatusDraft    DepositProductStatus = "DRAFT"
	DepositStatusActive   DepositProductStatus = "ACTIVE"
	DepositStatusPaused   DepositProductStatus = "PAUSED"
	DepositStatusArchived DepositProductStatus = "ARCHIVED"
)

func ParseDepositProductStatus(s string) (DepositProductStatus, error) {
	switch DepositProductStatus(strings.ToUpper(s)) {
	case DepositStatusDraft, DepositStatusActive, DepositStatusPaused, DepositStatusArchived:
		return DepositProductStatus(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid deposit product status: %s", s)
	}
}

type InterestCalcMethod string

const (
	InterestCalcDailyBalance  InterestCalcMethod = "DAILY_BALANCE"
	InterestCalcMinBalance    InterestCalcMethod = "MINIMUM_BALANCE"
	InterestCalcTiered        InterestCalcMethod = "TIERED"
)

type InterestPostingFreq string

const (
	PostingFreqMonthly    InterestPostingFreq = "MONTHLY"
	PostingFreqQuarterly  InterestPostingFreq = "QUARTERLY"
	PostingFreqAnnually   InterestPostingFreq = "ANNUALLY"
	PostingFreqOnMaturity InterestPostingFreq = "ON_MATURITY"
)

type InterestCompoundFreq string

const (
	CompoundFreqDaily   InterestCompoundFreq = "DAILY"
	CompoundFreqMonthly InterestCompoundFreq = "MONTHLY"
	CompoundFreqNone    InterestCompoundFreq = "NONE"
)

type AccrualFrequency string

const (
	AccrualFreqDaily   AccrualFrequency = "DAILY"
	AccrualFreqMonthly AccrualFrequency = "MONTHLY"
)

// ─── Deposit Product Entity ─────────────────────────────────────────────────

type DepositProduct struct {
	ID                        uuid.UUID              `json:"id"`
	TenantID                  string                 `json:"tenantId"`
	ProductCode               string                 `json:"productCode"`
	Name                      string                 `json:"name"`
	Description               *string                `json:"description,omitempty"`
	ProductCategory           DepositProductCategory `json:"productCategory"`
	Status                    DepositProductStatus   `json:"status"`
	Currency                  string                 `json:"currency"`
	InterestRate              decimal.Decimal        `json:"interestRate"`
	InterestCalcMethod        InterestCalcMethod     `json:"interestCalcMethod"`
	InterestPostingFreq       InterestPostingFreq    `json:"interestPostingFreq"`
	InterestCompoundFreq      InterestCompoundFreq   `json:"interestCompoundFreq"`
	AccrualFrequency          AccrualFrequency       `json:"accrualFrequency"`
	MinOpeningBalance         decimal.Decimal        `json:"minOpeningBalance"`
	MinOperatingBalance       decimal.Decimal        `json:"minOperatingBalance"`
	MinBalanceForInterest     decimal.Decimal        `json:"minBalanceForInterest"`
	MinTermDays               *int                   `json:"minTermDays,omitempty"`
	MaxTermDays               *int                   `json:"maxTermDays,omitempty"`
	EarlyWithdrawalPenaltyRate *decimal.Decimal      `json:"earlyWithdrawalPenaltyRate,omitempty"`
	AutoRenew                 bool                   `json:"autoRenew"`
	DormancyDaysThreshold     int                    `json:"dormancyDaysThreshold"`
	DormancyChargeAmount      *decimal.Decimal       `json:"dormancyChargeAmount,omitempty"`
	MonthlyMaintenanceFee     *decimal.Decimal       `json:"monthlyMaintenanceFee,omitempty"`
	MaxWithdrawalsPerMonth    *int                   `json:"maxWithdrawalsPerMonth,omitempty"`
	InterestTiers             []DepositInterestTier  `json:"interestTiers"`
	Version                   int                    `json:"version"`
	CreatedBy                 *string                `json:"createdBy,omitempty"`
	CreatedAt                 time.Time              `json:"createdAt"`
	UpdatedAt                 time.Time              `json:"updatedAt"`
}

type DepositInterestTier struct {
	ID         uuid.UUID       `json:"id"`
	ProductID  uuid.UUID       `json:"-"`
	FromAmount decimal.Decimal `json:"fromAmount"`
	ToAmount   decimal.Decimal `json:"toAmount"`
	Rate       decimal.Decimal `json:"rate"`
}

// ─── DTOs ───────────────────────────────────────────────────────────────────

type CreateDepositProductRequest struct {
	ProductCode               string           `json:"productCode"`
	Name                      string           `json:"name"`
	Description               *string          `json:"description"`
	ProductCategory           string           `json:"productCategory"`
	Currency                  *string          `json:"currency"`
	InterestRate              *decimal.Decimal `json:"interestRate"`
	InterestCalcMethod        *string          `json:"interestCalcMethod"`
	InterestPostingFreq       *string          `json:"interestPostingFreq"`
	InterestCompoundFreq      *string          `json:"interestCompoundFreq"`
	AccrualFrequency          *string          `json:"accrualFrequency"`
	MinOpeningBalance         *decimal.Decimal `json:"minOpeningBalance"`
	MinOperatingBalance       *decimal.Decimal `json:"minOperatingBalance"`
	MinBalanceForInterest     *decimal.Decimal `json:"minBalanceForInterest"`
	MinTermDays               *int             `json:"minTermDays"`
	MaxTermDays               *int             `json:"maxTermDays"`
	EarlyWithdrawalPenaltyRate *decimal.Decimal `json:"earlyWithdrawalPenaltyRate"`
	AutoRenew                 bool             `json:"autoRenew"`
	DormancyDaysThreshold     *int             `json:"dormancyDaysThreshold"`
	DormancyChargeAmount      *decimal.Decimal `json:"dormancyChargeAmount"`
	MonthlyMaintenanceFee     *decimal.Decimal `json:"monthlyMaintenanceFee"`
	MaxWithdrawalsPerMonth    *int             `json:"maxWithdrawalsPerMonth"`
	InterestTiers             []DepositTierRequest `json:"interestTiers"`
}

type DepositTierRequest struct {
	FromAmount decimal.Decimal `json:"fromAmount"`
	ToAmount   decimal.Decimal `json:"toAmount"`
	Rate       decimal.Decimal `json:"rate"`
}

type DepositProductResponse struct {
	ID                        uuid.UUID       `json:"id"`
	TenantID                  string          `json:"tenantId"`
	ProductCode               string          `json:"productCode"`
	Name                      string          `json:"name"`
	Description               *string         `json:"description,omitempty"`
	ProductCategory           string          `json:"productCategory"`
	Status                    string          `json:"status"`
	Currency                  string          `json:"currency"`
	InterestRate              decimal.Decimal `json:"interestRate"`
	InterestCalcMethod        string          `json:"interestCalcMethod"`
	InterestPostingFreq       string          `json:"interestPostingFreq"`
	InterestCompoundFreq      string          `json:"interestCompoundFreq"`
	AccrualFrequency          string          `json:"accrualFrequency"`
	MinOpeningBalance         decimal.Decimal `json:"minOpeningBalance"`
	MinOperatingBalance       decimal.Decimal `json:"minOperatingBalance"`
	MinBalanceForInterest     decimal.Decimal `json:"minBalanceForInterest"`
	MinTermDays               *int            `json:"minTermDays,omitempty"`
	MaxTermDays               *int            `json:"maxTermDays,omitempty"`
	EarlyWithdrawalPenaltyRate *decimal.Decimal `json:"earlyWithdrawalPenaltyRate,omitempty"`
	AutoRenew                 bool            `json:"autoRenew"`
	DormancyDaysThreshold     int             `json:"dormancyDaysThreshold"`
	DormancyChargeAmount      *decimal.Decimal `json:"dormancyChargeAmount,omitempty"`
	MonthlyMaintenanceFee     *decimal.Decimal `json:"monthlyMaintenanceFee,omitempty"`
	MaxWithdrawalsPerMonth    *int            `json:"maxWithdrawalsPerMonth,omitempty"`
	InterestTiers             []DepositTierResponse `json:"interestTiers"`
	Version                   int             `json:"version"`
	CreatedBy                 *string         `json:"createdBy,omitempty"`
	CreatedAt                 time.Time       `json:"createdAt"`
	UpdatedAt                 time.Time       `json:"updatedAt"`
}

type DepositTierResponse struct {
	ID         uuid.UUID       `json:"id"`
	FromAmount decimal.Decimal `json:"fromAmount"`
	ToAmount   decimal.Decimal `json:"toAmount"`
	Rate       decimal.Decimal `json:"rate"`
}

func DepositProductToResponse(p *DepositProduct) DepositProductResponse {
	tiers := make([]DepositTierResponse, 0, len(p.InterestTiers))
	for _, t := range p.InterestTiers {
		tiers = append(tiers, DepositTierResponse{
			ID:         t.ID,
			FromAmount: t.FromAmount,
			ToAmount:   t.ToAmount,
			Rate:       t.Rate,
		})
	}
	return DepositProductResponse{
		ID:                         p.ID,
		TenantID:                   p.TenantID,
		ProductCode:                p.ProductCode,
		Name:                       p.Name,
		Description:                p.Description,
		ProductCategory:            string(p.ProductCategory),
		Status:                     string(p.Status),
		Currency:                   p.Currency,
		InterestRate:               p.InterestRate,
		InterestCalcMethod:         string(p.InterestCalcMethod),
		InterestPostingFreq:        string(p.InterestPostingFreq),
		InterestCompoundFreq:       string(p.InterestCompoundFreq),
		AccrualFrequency:           string(p.AccrualFrequency),
		MinOpeningBalance:          p.MinOpeningBalance,
		MinOperatingBalance:        p.MinOperatingBalance,
		MinBalanceForInterest:      p.MinBalanceForInterest,
		MinTermDays:                p.MinTermDays,
		MaxTermDays:                p.MaxTermDays,
		EarlyWithdrawalPenaltyRate: p.EarlyWithdrawalPenaltyRate,
		AutoRenew:                  p.AutoRenew,
		DormancyDaysThreshold:      p.DormancyDaysThreshold,
		DormancyChargeAmount:       p.DormancyChargeAmount,
		MonthlyMaintenanceFee:      p.MonthlyMaintenanceFee,
		MaxWithdrawalsPerMonth:     p.MaxWithdrawalsPerMonth,
		InterestTiers:              tiers,
		Version:                    p.Version,
		CreatedBy:                  p.CreatedBy,
		CreatedAt:                  p.CreatedAt,
		UpdatedAt:                  p.UpdatedAt,
	}
}
