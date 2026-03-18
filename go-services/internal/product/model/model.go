package model

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

// ─── Enums ──────────────────────────────────────────────────────────────────

type ProductType string

const (
	ProductTypeNanoLoan     ProductType = "NANO_LOAN"
	ProductTypePersonalLoan ProductType = "PERSONAL_LOAN"
	ProductTypeBNPL         ProductType = "BNPL"
	ProductTypeSMELoan      ProductType = "SME_LOAN"
	ProductTypeGroupLoan    ProductType = "GROUP_LOAN"
)

func ParseProductType(s string) (ProductType, error) {
	switch ProductType(strings.ToUpper(s)) {
	case ProductTypeNanoLoan, ProductTypePersonalLoan, ProductTypeBNPL, ProductTypeSMELoan, ProductTypeGroupLoan:
		return ProductType(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid product type: %s", s)
	}
}

type ProductStatus string

const (
	ProductStatusDraft    ProductStatus = "DRAFT"
	ProductStatusActive   ProductStatus = "ACTIVE"
	ProductStatusPaused   ProductStatus = "PAUSED"
	ProductStatusInactive ProductStatus = "INACTIVE"
	ProductStatusArchived ProductStatus = "ARCHIVED"
)

type ScheduleType string

const (
	ScheduleTypeEMI         ScheduleType = "EMI"
	ScheduleTypeFlat        ScheduleType = "FLAT"
	ScheduleTypeFlatRate    ScheduleType = "FLAT_RATE"
	ScheduleTypeActuarial   ScheduleType = "ACTUARIAL"
	ScheduleTypeDailySimple ScheduleType = "DAILY_SIMPLE"
	ScheduleTypeBalloon     ScheduleType = "BALLOON"
	ScheduleTypeSeasonal    ScheduleType = "SEASONAL"
	ScheduleTypeGraduated   ScheduleType = "GRADUATED"
)

func ParseScheduleType(s string) (ScheduleType, error) {
	switch ScheduleType(strings.ToUpper(s)) {
	case ScheduleTypeEMI, ScheduleTypeFlat, ScheduleTypeFlatRate, ScheduleTypeActuarial,
		ScheduleTypeDailySimple, ScheduleTypeBalloon, ScheduleTypeSeasonal, ScheduleTypeGraduated:
		return ScheduleType(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid schedule type: %s", s)
	}
}

type RepaymentFrequency string

const (
	RepaymentFrequencyDaily     RepaymentFrequency = "DAILY"
	RepaymentFrequencyWeekly    RepaymentFrequency = "WEEKLY"
	RepaymentFrequencyBiweekly  RepaymentFrequency = "BIWEEKLY"
	RepaymentFrequencyMonthly   RepaymentFrequency = "MONTHLY"
	RepaymentFrequencyQuarterly RepaymentFrequency = "QUARTERLY"
	RepaymentFrequencyBullet    RepaymentFrequency = "BULLET"
)

func ParseRepaymentFrequency(s string) (RepaymentFrequency, error) {
	switch RepaymentFrequency(strings.ToUpper(s)) {
	case RepaymentFrequencyDaily, RepaymentFrequencyWeekly, RepaymentFrequencyBiweekly,
		RepaymentFrequencyMonthly, RepaymentFrequencyQuarterly, RepaymentFrequencyBullet:
		return RepaymentFrequency(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid repayment frequency: %s", s)
	}
}

// DaysInPeriod returns the number of days in one period for this frequency.
func (rf RepaymentFrequency) DaysInPeriod() int {
	switch rf {
	case RepaymentFrequencyDaily:
		return 1
	case RepaymentFrequencyWeekly:
		return 7
	case RepaymentFrequencyBiweekly:
		return 14
	case RepaymentFrequencyMonthly:
		return 30
	case RepaymentFrequencyQuarterly:
		return 91
	case RepaymentFrequencyBullet:
		return 0
	default:
		return 30
	}
}

// PeriodsPerYear returns the number of periods in a year for this frequency.
func (rf RepaymentFrequency) PeriodsPerYear() int {
	switch rf {
	case RepaymentFrequencyDaily:
		return 365
	case RepaymentFrequencyWeekly:
		return 52
	case RepaymentFrequencyBiweekly:
		return 26
	case RepaymentFrequencyMonthly:
		return 12
	case RepaymentFrequencyQuarterly:
		return 4
	case RepaymentFrequencyBullet:
		return 1
	default:
		return 12
	}
}

type FeeType string

const (
	FeeTypeUpfront      FeeType = "UPFRONT"
	FeeTypeDisbursement FeeType = "DISBURSEMENT"
	FeeTypeAnnual       FeeType = "ANNUAL"
	FeeTypeMonthly      FeeType = "MONTHLY"
	FeeTypeExit         FeeType = "EXIT"
	FeeTypePenalty      FeeType = "PENALTY"
)

func ParseFeeType(s string) (FeeType, error) {
	switch FeeType(strings.ToUpper(s)) {
	case FeeTypeUpfront, FeeTypeDisbursement, FeeTypeAnnual, FeeTypeMonthly, FeeTypeExit, FeeTypePenalty:
		return FeeType(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid fee type: %s", s)
	}
}

type CalculationType string

const (
	CalculationTypeFlat       CalculationType = "FLAT"
	CalculationTypePercentage CalculationType = "PERCENTAGE"
)

func ParseCalculationType(s string) (CalculationType, error) {
	switch CalculationType(strings.ToUpper(s)) {
	case CalculationTypeFlat, CalculationTypePercentage:
		return CalculationType(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid calculation type: %s", s)
	}
}

type ChargeCalculationType string

const (
	ChargeCalculationTypeFlat       ChargeCalculationType = "FLAT"
	ChargeCalculationTypePercentage ChargeCalculationType = "PERCENTAGE"
	ChargeCalculationTypeTiered     ChargeCalculationType = "TIERED"
)

func ParseChargeCalculationType(s string) (ChargeCalculationType, error) {
	switch ChargeCalculationType(strings.ToUpper(s)) {
	case ChargeCalculationTypeFlat, ChargeCalculationTypePercentage, ChargeCalculationTypeTiered:
		return ChargeCalculationType(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid charge calculation type: %s", s)
	}
}

type ChargeTransactionType string

const (
	ChargeTransactionTypeTransferInternal   ChargeTransactionType = "TRANSFER_INTERNAL"
	ChargeTransactionTypeTransferThirdParty ChargeTransactionType = "TRANSFER_THIRD_PARTY"
	ChargeTransactionTypeTransferWallet     ChargeTransactionType = "TRANSFER_WALLET"
	ChargeTransactionTypeWithdrawal         ChargeTransactionType = "WITHDRAWAL"
	ChargeTransactionTypeDeposit            ChargeTransactionType = "DEPOSIT"
	ChargeTransactionTypeStatementRequest   ChargeTransactionType = "STATEMENT_REQUEST"
)

func ParseChargeTransactionType(s string) (ChargeTransactionType, error) {
	switch ChargeTransactionType(strings.ToUpper(s)) {
	case ChargeTransactionTypeTransferInternal, ChargeTransactionTypeTransferThirdParty,
		ChargeTransactionTypeTransferWallet, ChargeTransactionTypeWithdrawal,
		ChargeTransactionTypeDeposit, ChargeTransactionTypeStatementRequest:
		return ChargeTransactionType(strings.ToUpper(s)), nil
	default:
		return "", fmt.Errorf("invalid charge transaction type: %s", s)
	}
}

// ─── Entities ───────────────────────────────────────────────────────────────

type Product struct {
	ID                    uuid.UUID          `json:"id"`
	TenantID              string             `json:"tenantId"`
	ProductCode           string             `json:"productCode"`
	Name                  string             `json:"name"`
	ProductType           ProductType        `json:"productType"`
	Status                ProductStatus      `json:"status"`
	Description           *string            `json:"description"`
	Currency              string             `json:"currency"`
	MinAmount             *decimal.Decimal    `json:"minAmount"`
	MaxAmount             *decimal.Decimal    `json:"maxAmount"`
	MinTenorDays          *int               `json:"minTenorDays"`
	MaxTenorDays          *int               `json:"maxTenorDays"`
	ScheduleType          ScheduleType       `json:"scheduleType"`
	RepaymentFrequency    RepaymentFrequency `json:"repaymentFrequency"`
	NominalRate           decimal.Decimal    `json:"nominalRate"`
	PenaltyRate           decimal.Decimal    `json:"penaltyRate"`
	PenaltyGraceDays      int                `json:"penaltyGraceDays"`
	GracePeriodDays       int                `json:"gracePeriodDays"`
	ProcessingFeeRate     decimal.Decimal    `json:"processingFeeRate"`
	ProcessingFeeMin      decimal.Decimal    `json:"processingFeeMin"`
	ProcessingFeeMax      *decimal.Decimal    `json:"processingFeeMax"`
	RequiresCollateral    bool               `json:"requiresCollateral"`
	MinCreditScore        int                `json:"minCreditScore"`
	MaxDtir               decimal.Decimal    `json:"maxDtir"`
	Version               int                `json:"version"`
	TemplateID            *string            `json:"templateId"`
	RequiresTwoPersonAuth bool               `json:"requiresTwoPersonAuth"`
	AuthThresholdAmount   *decimal.Decimal    `json:"authThresholdAmount"`
	PendingAuthorization  bool               `json:"pendingAuthorization"`
	CreatedBy             *string            `json:"createdBy"`
	Fees                  []ProductFee       `json:"fees"`
	CreatedAt             time.Time          `json:"createdAt"`
	UpdatedAt             time.Time          `json:"updatedAt"`
}

type ProductFee struct {
	ID              uuid.UUID       `json:"id"`
	TenantID        string          `json:"-"`
	ProductID       uuid.UUID       `json:"-"`
	FeeName         string          `json:"feeName"`
	FeeType         FeeType         `json:"feeType"`
	CalculationType CalculationType `json:"calculationType"`
	Amount          *decimal.Decimal `json:"amount"`
	Rate            *decimal.Decimal `json:"rate"`
	IsMandatory     bool            `json:"isMandatory"`
	CreatedAt       time.Time       `json:"-"`
}

type ProductTemplate struct {
	ID            uuid.UUID   `json:"id"`
	TemplateCode  string      `json:"templateCode"`
	Name          string      `json:"name"`
	ProductType   ProductType `json:"productType"`
	Configuration string      `json:"configuration"`
	IsActive      bool        `json:"isActive"`
	CreatedAt     time.Time   `json:"createdAt"`
}

type ProductVersion struct {
	ID            uuid.UUID       `json:"id"`
	ProductID     uuid.UUID       `json:"productId"`
	VersionNumber int             `json:"versionNumber"`
	Snapshot      json.RawMessage `json:"snapshot"`
	ChangedBy     *string         `json:"changedBy"`
	ChangeReason  *string         `json:"changeReason"`
	CreatedAt     time.Time       `json:"createdAt"`
}

type TransactionCharge struct {
	ID              uuid.UUID             `json:"id"`
	TenantID        string                `json:"tenantId"`
	ChargeCode      string                `json:"chargeCode"`
	ChargeName      string                `json:"chargeName"`
	TransactionType ChargeTransactionType `json:"transactionType"`
	CalculationType ChargeCalculationType `json:"calculationType"`
	FlatAmount      *decimal.Decimal       `json:"flatAmount"`
	PercentageRate  *decimal.Decimal       `json:"percentageRate"`
	MinAmount       *decimal.Decimal       `json:"minAmount"`
	MaxAmount       *decimal.Decimal       `json:"maxAmount"`
	Currency        string                `json:"currency"`
	IsActive        bool                  `json:"isActive"`
	EffectiveFrom   *time.Time            `json:"effectiveFrom"`
	EffectiveTo     *time.Time            `json:"effectiveTo"`
	Tiers           []ChargeTier          `json:"tiers"`
	CreatedAt       time.Time             `json:"createdAt"`
	UpdatedAt       time.Time             `json:"updatedAt"`
}

type ChargeTier struct {
	ID             uuid.UUID        `json:"id"`
	ChargeID       uuid.UUID        `json:"-"`
	FromAmount     decimal.Decimal  `json:"fromAmount"`
	ToAmount       decimal.Decimal  `json:"toAmount"`
	FlatAmount     *decimal.Decimal `json:"flatAmount"`
	PercentageRate *decimal.Decimal `json:"percentageRate"`
}

// ─── Request / Response DTOs ────────────────────────────────────────────────

type CreateProductRequest struct {
	ProductCode        string               `json:"productCode"`
	Name               string               `json:"name"`
	ProductType        string               `json:"productType"`
	Description        *string              `json:"description"`
	Currency           *string              `json:"currency"`
	MinAmount          *decimal.Decimal      `json:"minAmount"`
	MaxAmount          *decimal.Decimal      `json:"maxAmount"`
	MinTenorDays       *int                 `json:"minTenorDays"`
	MaxTenorDays       *int                 `json:"maxTenorDays"`
	ScheduleType       *string              `json:"scheduleType"`
	RepaymentFrequency *string              `json:"repaymentFrequency"`
	NominalRate        *decimal.Decimal      `json:"nominalRate"`
	PenaltyRate        *decimal.Decimal      `json:"penaltyRate"`
	PenaltyGraceDays   *int                 `json:"penaltyGraceDays"`
	GracePeriodDays    *int                 `json:"gracePeriodDays"`
	ProcessingFeeRate  *decimal.Decimal      `json:"processingFeeRate"`
	ProcessingFeeMin   *decimal.Decimal      `json:"processingFeeMin"`
	ProcessingFeeMax   *decimal.Decimal      `json:"processingFeeMax"`
	RequiresCollateral bool                 `json:"requiresCollateral"`
	MinCreditScore     int                  `json:"minCreditScore"`
	MaxDtir            *decimal.Decimal      `json:"maxDtir"`
	Fees               []FeeRequest         `json:"fees"`
	RequiresTwoPersonAuth bool              `json:"requiresTwoPersonAuth"`
	AuthThresholdAmount *decimal.Decimal     `json:"authThresholdAmount"`
	TemplateID         *string              `json:"templateId"`
}

type FeeRequest struct {
	FeeName         string           `json:"feeName"`
	FeeType         string           `json:"feeType"`
	CalculationType string           `json:"calculationType"`
	Amount          *decimal.Decimal `json:"amount"`
	Rate            *decimal.Decimal `json:"rate"`
	IsMandatory     bool             `json:"isMandatory"`
}

type SimulateScheduleRequest struct {
	Principal          decimal.Decimal    `json:"principal"`
	NominalRate        decimal.Decimal    `json:"nominalRate"`
	TenorDays          int                `json:"tenorDays"`
	ScheduleType       ScheduleType       `json:"scheduleType"`
	RepaymentFrequency RepaymentFrequency `json:"repaymentFrequency"`
	DisbursementDate   *string            `json:"disbursementDate"`
}

type CreateChargeRequest struct {
	ChargeCode      string            `json:"chargeCode"`
	ChargeName      string            `json:"chargeName"`
	TransactionType string            `json:"transactionType"`
	CalculationType string            `json:"calculationType"`
	FlatAmount      *decimal.Decimal  `json:"flatAmount"`
	PercentageRate  *decimal.Decimal  `json:"percentageRate"`
	MinAmount       *decimal.Decimal  `json:"minAmount"`
	MaxAmount       *decimal.Decimal  `json:"maxAmount"`
	Currency        *string           `json:"currency"`
	Tiers           []ChargeTierRequest `json:"tiers"`
}

type ChargeTierRequest struct {
	FromAmount     decimal.Decimal  `json:"fromAmount"`
	ToAmount       decimal.Decimal  `json:"toAmount"`
	FlatAmount     *decimal.Decimal `json:"flatAmount"`
	PercentageRate *decimal.Decimal `json:"percentageRate"`
}

// ─── Response DTOs ──────────────────────────────────────────────────────────

type ProductResponse struct {
	ID                    uuid.UUID          `json:"id"`
	TenantID              string             `json:"tenantId"`
	ProductCode           string             `json:"productCode"`
	Name                  string             `json:"name"`
	ProductType           string             `json:"productType"`
	Status                string             `json:"status"`
	Description           *string            `json:"description"`
	Currency              string             `json:"currency"`
	MinAmount             *decimal.Decimal    `json:"minAmount"`
	MaxAmount             *decimal.Decimal    `json:"maxAmount"`
	MinTenorDays          *int               `json:"minTenorDays"`
	MaxTenorDays          *int               `json:"maxTenorDays"`
	ScheduleType          string             `json:"scheduleType"`
	RepaymentFrequency    string             `json:"repaymentFrequency"`
	NominalRate           decimal.Decimal    `json:"nominalRate"`
	PenaltyRate           decimal.Decimal    `json:"penaltyRate"`
	PenaltyGraceDays      int                `json:"penaltyGraceDays"`
	GracePeriodDays       int                `json:"gracePeriodDays"`
	ProcessingFeeRate     decimal.Decimal    `json:"processingFeeRate"`
	Version               int                `json:"version"`
	TemplateID            *string            `json:"templateId"`
	RequiresTwoPersonAuth bool               `json:"requiresTwoPersonAuth"`
	PendingAuthorization  bool               `json:"pendingAuthorization"`
	CreatedBy             *string            `json:"createdBy"`
	Fees                  []ProductFeeResponse `json:"fees"`
	CreatedAt             time.Time          `json:"createdAt"`
	UpdatedAt             time.Time          `json:"updatedAt"`
}

type ProductFeeResponse struct {
	ID              uuid.UUID        `json:"id"`
	FeeName         string           `json:"feeName"`
	FeeType         string           `json:"feeType"`
	CalculationType string           `json:"calculationType"`
	Amount          *decimal.Decimal `json:"amount"`
	Rate            *decimal.Decimal `json:"rate"`
	IsMandatory     bool             `json:"isMandatory"`
}

type ScheduleResponse struct {
	ScheduleType         string              `json:"scheduleType"`
	Principal            decimal.Decimal     `json:"principal"`
	TotalInterest        decimal.Decimal     `json:"totalInterest"`
	TotalPayable         decimal.Decimal     `json:"totalPayable"`
	EffectiveRate        decimal.Decimal     `json:"effectiveRate"`
	NumberOfInstallments int                 `json:"numberOfInstallments"`
	Installments         []InstallmentResponse `json:"installments"`
}

type InstallmentResponse struct {
	InstallmentNumber  int             `json:"installmentNumber"`
	DueDate            string          `json:"dueDate"`
	Principal          decimal.Decimal `json:"principal"`
	Interest           decimal.Decimal `json:"interest"`
	TotalPayment       decimal.Decimal `json:"totalPayment"`
	OutstandingBalance decimal.Decimal `json:"outstandingBalance"`
}

type TransactionChargeResponse struct {
	ID              uuid.UUID            `json:"id"`
	ChargeCode      string               `json:"chargeCode"`
	ChargeName      string               `json:"chargeName"`
	TransactionType string               `json:"transactionType"`
	CalculationType string               `json:"calculationType"`
	FlatAmount      *decimal.Decimal     `json:"flatAmount"`
	PercentageRate  *decimal.Decimal     `json:"percentageRate"`
	MinAmount       *decimal.Decimal     `json:"minAmount"`
	MaxAmount       *decimal.Decimal     `json:"maxAmount"`
	Currency        string               `json:"currency"`
	IsActive        bool                 `json:"isActive"`
	EffectiveFrom   *time.Time           `json:"effectiveFrom"`
	EffectiveTo     *time.Time           `json:"effectiveTo"`
	Tiers           []TierResponse       `json:"tiers"`
	CreatedAt       time.Time            `json:"createdAt"`
	UpdatedAt       time.Time            `json:"updatedAt"`
}

type TierResponse struct {
	ID             uuid.UUID        `json:"id"`
	FromAmount     decimal.Decimal  `json:"fromAmount"`
	ToAmount       decimal.Decimal  `json:"toAmount"`
	FlatAmount     *decimal.Decimal `json:"flatAmount"`
	PercentageRate *decimal.Decimal `json:"percentageRate"`
}

type ChargeCalculationResponse struct {
	ChargeCode        string          `json:"chargeCode"`
	ChargeName        string          `json:"chargeName"`
	TransactionType   string          `json:"transactionType"`
	TransactionAmount decimal.Decimal `json:"transactionAmount"`
	ChargeAmount      decimal.Decimal `json:"chargeAmount"`
	Currency          string          `json:"currency"`
}

// ─── Conversion helpers ─────────────────────────────────────────────────────

func ProductToResponse(p *Product) ProductResponse {
	fees := make([]ProductFeeResponse, 0, len(p.Fees))
	for _, f := range p.Fees {
		fees = append(fees, ProductFeeResponse{
			ID:              f.ID,
			FeeName:         f.FeeName,
			FeeType:         string(f.FeeType),
			CalculationType: string(f.CalculationType),
			Amount:          f.Amount,
			Rate:            f.Rate,
			IsMandatory:     f.IsMandatory,
		})
	}
	return ProductResponse{
		ID:                    p.ID,
		TenantID:              p.TenantID,
		ProductCode:           p.ProductCode,
		Name:                  p.Name,
		ProductType:           string(p.ProductType),
		Status:                string(p.Status),
		Description:           p.Description,
		Currency:              p.Currency,
		MinAmount:             p.MinAmount,
		MaxAmount:             p.MaxAmount,
		MinTenorDays:          p.MinTenorDays,
		MaxTenorDays:          p.MaxTenorDays,
		ScheduleType:          string(p.ScheduleType),
		RepaymentFrequency:    string(p.RepaymentFrequency),
		NominalRate:           p.NominalRate,
		PenaltyRate:           p.PenaltyRate,
		PenaltyGraceDays:      p.PenaltyGraceDays,
		GracePeriodDays:       p.GracePeriodDays,
		ProcessingFeeRate:     p.ProcessingFeeRate,
		Version:               p.Version,
		TemplateID:            p.TemplateID,
		RequiresTwoPersonAuth: p.RequiresTwoPersonAuth,
		PendingAuthorization:  p.PendingAuthorization,
		CreatedBy:             p.CreatedBy,
		Fees:                  fees,
		CreatedAt:             p.CreatedAt,
		UpdatedAt:             p.UpdatedAt,
	}
}

func ChargeToResponse(c *TransactionCharge) TransactionChargeResponse {
	tiers := make([]TierResponse, 0, len(c.Tiers))
	for _, t := range c.Tiers {
		tiers = append(tiers, TierResponse{
			ID:             t.ID,
			FromAmount:     t.FromAmount,
			ToAmount:       t.ToAmount,
			FlatAmount:     t.FlatAmount,
			PercentageRate: t.PercentageRate,
		})
	}
	return TransactionChargeResponse{
		ID:              c.ID,
		ChargeCode:      c.ChargeCode,
		ChargeName:      c.ChargeName,
		TransactionType: string(c.TransactionType),
		CalculationType: string(c.CalculationType),
		FlatAmount:      c.FlatAmount,
		PercentageRate:  c.PercentageRate,
		MinAmount:       c.MinAmount,
		MaxAmount:       c.MaxAmount,
		Currency:        c.Currency,
		IsActive:        c.IsActive,
		EffectiveFrom:   c.EffectiveFrom,
		EffectiveTo:     c.EffectiveTo,
		Tiers:           tiers,
		CreatedAt:       c.CreatedAt,
		UpdatedAt:       c.UpdatedAt,
	}
}
