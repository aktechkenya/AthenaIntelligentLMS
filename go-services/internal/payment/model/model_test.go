package model

import (
	"encoding/json"
	"testing"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPaymentStatusConstants(t *testing.T) {
	assert.True(t, ValidPaymentStatuses[PaymentStatusPending])
	assert.True(t, ValidPaymentStatuses[PaymentStatusProcessing])
	assert.True(t, ValidPaymentStatuses[PaymentStatusCompleted])
	assert.True(t, ValidPaymentStatuses[PaymentStatusFailed])
	assert.True(t, ValidPaymentStatuses[PaymentStatusReversed])
	assert.False(t, ValidPaymentStatuses["UNKNOWN"])
}

func TestPaymentTypeConstants(t *testing.T) {
	assert.True(t, ValidPaymentTypes[PaymentTypeLoanDisbursement])
	assert.True(t, ValidPaymentTypes[PaymentTypeLoanRepayment])
	assert.True(t, ValidPaymentTypes[PaymentTypeFee])
	assert.True(t, ValidPaymentTypes[PaymentTypePenalty])
	assert.True(t, ValidPaymentTypes[PaymentTypeFloatTransfer])
	assert.True(t, ValidPaymentTypes[PaymentTypeOther])
	assert.False(t, ValidPaymentTypes["INVALID"])
}

func TestPaymentChannelConstants(t *testing.T) {
	assert.True(t, ValidPaymentChannels[PaymentChannelMpesa])
	assert.True(t, ValidPaymentChannels[PaymentChannelBankTransfer])
	assert.True(t, ValidPaymentChannels[PaymentChannelCard])
	assert.True(t, ValidPaymentChannels[PaymentChannelCash])
	assert.True(t, ValidPaymentChannels[PaymentChannelInternal])
	assert.False(t, ValidPaymentChannels["BITCOIN"])
}

func TestPaymentMethodTypeConstants(t *testing.T) {
	assert.True(t, ValidPaymentMethodTypes[PaymentMethodTypeMpesa])
	assert.True(t, ValidPaymentMethodTypes[PaymentMethodTypeBankAccount])
	assert.True(t, ValidPaymentMethodTypes[PaymentMethodTypeCard])
	assert.False(t, ValidPaymentMethodTypes["CRYPTO"])
}

func TestPaymentResponseJSON_CamelCase(t *testing.T) {
	loanID := uuid.New()
	resp := PaymentResponse{
		ID:                uuid.New(),
		TenantID:          "tenant1",
		CustomerID:        "CUST-001",
		LoanID:            &loanID,
		PaymentType:       PaymentTypeLoanRepayment,
		PaymentChannel:    PaymentChannelMpesa,
		Status:            PaymentStatusCompleted,
		Amount:            decimal.NewFromFloat(500.00),
		Currency:          "KES",
		InternalReference: "INT-123",
	}

	data, err := json.Marshal(resp)
	require.NoError(t, err)

	// Verify camelCase keys
	var raw map[string]any
	require.NoError(t, json.Unmarshal(data, &raw))

	assert.Contains(t, raw, "tenantId")
	assert.Contains(t, raw, "customerId")
	assert.Contains(t, raw, "loanId")
	assert.Contains(t, raw, "paymentType")
	assert.Contains(t, raw, "paymentChannel")
	assert.Contains(t, raw, "internalReference")
	assert.Contains(t, raw, "initiatedAt")
	assert.Contains(t, raw, "createdAt")

	// Must NOT contain snake_case keys
	assert.NotContains(t, raw, "tenant_id")
	assert.NotContains(t, raw, "customer_id")
	assert.NotContains(t, raw, "payment_type")
	assert.NotContains(t, raw, "internal_reference")
}

func TestPaymentMethodResponseJSON_CamelCase(t *testing.T) {
	resp := PaymentMethodResponse{
		ID:            uuid.New(),
		CustomerID:    "CUST-001",
		MethodType:    PaymentMethodTypeBankAccount,
		AccountNumber: "1234567890",
		IsDefault:     true,
		IsActive:      true,
	}

	data, err := json.Marshal(resp)
	require.NoError(t, err)

	var raw map[string]any
	require.NoError(t, json.Unmarshal(data, &raw))

	assert.Contains(t, raw, "customerId")
	assert.Contains(t, raw, "methodType")
	assert.Contains(t, raw, "accountNumber")
	assert.Contains(t, raw, "isDefault")
	assert.Contains(t, raw, "isActive")
	assert.NotContains(t, raw, "customer_id")
	assert.NotContains(t, raw, "method_type")
}

func TestInitiatePaymentRequestJSON_Unmarshal(t *testing.T) {
	input := `{
		"customerId": "CUST-001",
		"paymentType": "LOAN_REPAYMENT",
		"paymentChannel": "MPESA",
		"amount": "1500.75",
		"currency": "KES"
	}`

	var req InitiatePaymentRequest
	err := json.Unmarshal([]byte(input), &req)
	require.NoError(t, err)

	assert.Equal(t, "CUST-001", req.CustomerID)
	assert.Equal(t, PaymentTypeLoanRepayment, req.PaymentType)
	assert.Equal(t, PaymentChannelMpesa, req.PaymentChannel)
	assert.True(t, req.Amount.Equal(decimal.NewFromFloat(1500.75)))
	assert.Equal(t, "KES", req.Currency)
	assert.Nil(t, req.LoanID)
}

func TestPaymentStatusValues(t *testing.T) {
	// Verify string values match Java enum names
	assert.Equal(t, PaymentStatus("PENDING"), PaymentStatusPending)
	assert.Equal(t, PaymentStatus("PROCESSING"), PaymentStatusProcessing)
	assert.Equal(t, PaymentStatus("COMPLETED"), PaymentStatusCompleted)
	assert.Equal(t, PaymentStatus("FAILED"), PaymentStatusFailed)
	assert.Equal(t, PaymentStatus("REVERSED"), PaymentStatusReversed)
}

func TestPaymentTypeValues(t *testing.T) {
	assert.Equal(t, PaymentType("LOAN_DISBURSEMENT"), PaymentTypeLoanDisbursement)
	assert.Equal(t, PaymentType("LOAN_REPAYMENT"), PaymentTypeLoanRepayment)
	assert.Equal(t, PaymentType("FEE"), PaymentTypeFee)
	assert.Equal(t, PaymentType("PENALTY"), PaymentTypePenalty)
	assert.Equal(t, PaymentType("FLOAT_TRANSFER"), PaymentTypeFloatTransfer)
	assert.Equal(t, PaymentType("OTHER"), PaymentTypeOther)
}
