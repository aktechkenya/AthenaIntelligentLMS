package service

import (
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"

	"github.com/athena-lms/go-services/internal/payment/model"
)

func TestToResponse(t *testing.T) {
	now := time.Now()
	loanID := uuid.New()
	appID := uuid.New()
	extRef := "EXT-123"
	desc := "test payment"
	createdBy := "admin"

	p := &model.Payment{
		ID:                uuid.New(),
		TenantID:          "tenant1",
		CustomerID:        "CUST-001",
		LoanID:            &loanID,
		ApplicationID:     &appID,
		PaymentType:       model.PaymentTypeLoanRepayment,
		PaymentChannel:    model.PaymentChannelMpesa,
		Status:            model.PaymentStatusCompleted,
		Amount:            decimal.NewFromFloat(1000.50),
		Currency:          "KES",
		ExternalReference: &extRef,
		InternalReference: "INT-456",
		Description:       &desc,
		InitiatedAt:       now,
		CreatedAt:         now,
		UpdatedAt:         now,
		CreatedBy:         &createdBy,
	}

	resp := ToResponse(p)

	assert.Equal(t, p.ID, resp.ID)
	assert.Equal(t, "tenant1", resp.TenantID)
	assert.Equal(t, "CUST-001", resp.CustomerID)
	assert.Equal(t, &loanID, resp.LoanID)
	assert.Equal(t, &appID, resp.ApplicationID)
	assert.Equal(t, model.PaymentTypeLoanRepayment, resp.PaymentType)
	assert.Equal(t, model.PaymentChannelMpesa, resp.PaymentChannel)
	assert.Equal(t, model.PaymentStatusCompleted, resp.Status)
	assert.True(t, resp.Amount.Equal(decimal.NewFromFloat(1000.50)))
	assert.Equal(t, "KES", resp.Currency)
	assert.Equal(t, &extRef, resp.ExternalReference)
	assert.Equal(t, "INT-456", resp.InternalReference)
	assert.Equal(t, &desc, resp.Description)
	assert.Nil(t, resp.FailureReason)
	assert.Nil(t, resp.ReversalReason)
}

func TestToMethodResponse(t *testing.T) {
	now := time.Now()
	alias := "My M-Pesa"
	acctName := "John Doe"
	provider := "Safaricom"

	m := &model.PaymentMethod{
		ID:            uuid.New(),
		TenantID:      "tenant1",
		CustomerID:    "CUST-001",
		MethodType:    model.PaymentMethodTypeMpesa,
		Alias:         &alias,
		AccountNumber: "254712345678",
		AccountName:   &acctName,
		Provider:      &provider,
		IsDefault:     true,
		IsActive:      true,
		CreatedAt:     now,
		UpdatedAt:     now,
	}

	resp := ToMethodResponse(m)

	assert.Equal(t, m.ID, resp.ID)
	assert.Equal(t, "CUST-001", resp.CustomerID)
	assert.Equal(t, model.PaymentMethodTypeMpesa, resp.MethodType)
	assert.Equal(t, &alias, resp.Alias)
	assert.Equal(t, "254712345678", resp.AccountNumber)
	assert.Equal(t, &acctName, resp.AccountName)
	assert.Equal(t, &provider, resp.Provider)
	assert.True(t, resp.IsDefault)
	assert.True(t, resp.IsActive)
}
