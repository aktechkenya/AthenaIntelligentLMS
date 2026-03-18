package service

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"

	"github.com/athena-lms/go-services/internal/management/model"
)

// ScheduleGenerator generates repayment schedules for EMI and FLAT_RATE loans.
// All arithmetic uses shopspring/decimal for financial precision.
type ScheduleGenerator struct{}

// NewScheduleGenerator creates a new ScheduleGenerator.
func NewScheduleGenerator() *ScheduleGenerator {
	return &ScheduleGenerator{}
}

// Generate creates a repayment schedule for the given loan.
func (g *ScheduleGenerator) Generate(loan *model.Loan) []*model.LoanSchedule {
	n := numberOfInstallments(loan.TenorMonths, loan.RepaymentFrequency)
	principal := loan.DisbursedAmount
	annualRate := loan.InterestRate.Div(decimal.NewFromInt(100))

	if loan.ScheduleType == model.ScheduleTypeEMI {
		return g.generateEMI(loan, principal, annualRate, n)
	}
	return g.generateFlatRate(loan, principal, annualRate, n)
}

// generateEMI produces an EMI (reducing-balance) schedule.
// EMI = P * r * (1+r)^n / ((1+r)^n - 1)
func (g *ScheduleGenerator) generateEMI(loan *model.Loan, principal, annualRate decimal.Decimal, n int) []*model.LoanSchedule {
	periodicRate := periodicRate(annualRate, loan.RepaymentFrequency)
	var emi decimal.Decimal

	if periodicRate.IsZero() {
		// Zero-interest: simple division
		emi = principal.Div(decimal.NewFromInt(int64(n))).Round(2)
	} else {
		// EMI = P * r * (1+r)^n / ((1+r)^n - 1)
		onePlusR := decimal.NewFromInt(1).Add(periodicRate)
		// (1+r)^n using iterative multiplication for precision
		onePlusRpowN := decPow(onePlusR, n)
		numerator := principal.Mul(periodicRate).Mul(onePlusRpowN)
		denominator := onePlusRpowN.Sub(decimal.NewFromInt(1))
		emi = numerator.Div(denominator).Round(2)
	}

	schedules := make([]*model.LoanSchedule, 0, n)
	balance := principal
	dueDate := loan.FirstRepaymentDate

	for i := 1; i <= n; i++ {
		interestDue := balance.Mul(periodicRate).Round(2)
		var principalDue decimal.Decimal
		if i == n {
			// Last installment: pay remaining balance
			principalDue = balance
		} else {
			principalDue = emi.Sub(interestDue).Round(2)
		}

		// Safeguard: principal cannot exceed balance
		if principalDue.GreaterThan(balance) {
			principalDue = balance
		}

		totalDue := principalDue.Add(interestDue)
		schedules = append(schedules, buildInstallment(loan, i, dueDate, principalDue, interestDue, totalDue))
		balance = balance.Sub(principalDue)
		dueDate = nextDueDate(dueDate, loan.RepaymentFrequency)
	}

	return schedules
}

// generateFlatRate produces a flat-rate schedule.
// totalRepayable = P + P * annualRate * years
// installment = totalRepayable / n
func (g *ScheduleGenerator) generateFlatRate(loan *model.Loan, principal, annualRate decimal.Decimal, n int) []*model.LoanSchedule {
	years := decimal.NewFromInt(int64(loan.TenorMonths)).Div(decimal.NewFromInt(12))
	totalInterest := principal.Mul(annualRate).Mul(years)
	nDec := decimal.NewFromInt(int64(n))
	principalPerInst := principal.Div(nDec).Round(2)
	interestPerInst := totalInterest.Div(nDec).Round(2)

	schedules := make([]*model.LoanSchedule, 0, n)
	dueDate := loan.FirstRepaymentDate

	for i := 1; i <= n; i++ {
		pDue := principalPerInst
		if i == n {
			// Last installment: pick up any rounding remainder
			pDue = principal.Sub(principalPerInst.Mul(decimal.NewFromInt(int64(n - 1))))
		}
		totalDue := pDue.Add(interestPerInst)
		schedules = append(schedules, buildInstallment(loan, i, dueDate, pDue, interestPerInst, totalDue))
		dueDate = nextDueDate(dueDate, loan.RepaymentFrequency)
	}

	return schedules
}

func buildInstallment(loan *model.Loan, no int, dueDate time.Time, principalDue, interestDue, totalDue decimal.Decimal) *model.LoanSchedule {
	zero := decimal.Zero
	return &model.LoanSchedule{
		ID:            uuid.Nil, // will be set by DB
		LoanID:        loan.ID,
		TenantID:      loan.TenantID,
		InstallmentNo: no,
		DueDate:       dueDate,
		PrincipalDue:  principalDue,
		InterestDue:   interestDue,
		FeeDue:        zero,
		PenaltyDue:    zero,
		TotalDue:      totalDue,
		PrincipalPaid: zero,
		InterestPaid:  zero,
		FeePaid:       zero,
		PenaltyPaid:   zero,
		TotalPaid:     zero,
		Status:        model.InstallmentPending,
	}
}

func numberOfInstallments(tenorMonths int, freq model.RepaymentFrequency) int {
	switch freq {
	case model.FrequencyDaily:
		return tenorMonths * 30
	case model.FrequencyWeekly:
		return tenorMonths * 4
	case model.FrequencyBiweekly:
		return tenorMonths * 2
	case model.FrequencyMonthly:
		return tenorMonths
	default:
		return tenorMonths
	}
}

func periodicRate(annualRate decimal.Decimal, freq model.RepaymentFrequency) decimal.Decimal {
	var periods int64
	switch freq {
	case model.FrequencyDaily:
		periods = 360
	case model.FrequencyWeekly:
		periods = 52
	case model.FrequencyBiweekly:
		periods = 26
	case model.FrequencyMonthly:
		periods = 12
	default:
		periods = 12
	}
	return annualRate.Div(decimal.NewFromInt(periods))
}

func nextDueDate(current time.Time, freq model.RepaymentFrequency) time.Time {
	switch freq {
	case model.FrequencyDaily:
		return current.AddDate(0, 0, 1)
	case model.FrequencyWeekly:
		return current.AddDate(0, 0, 7)
	case model.FrequencyBiweekly:
		return current.AddDate(0, 0, 14)
	case model.FrequencyMonthly:
		return current.AddDate(0, 1, 0)
	default:
		return current.AddDate(0, 1, 0)
	}
}

// decPow computes base^exp for decimal using iterative multiplication.
func decPow(base decimal.Decimal, exp int) decimal.Decimal {
	result := decimal.NewFromInt(1)
	for i := 0; i < exp; i++ {
		result = result.Mul(base)
	}
	return result
}
