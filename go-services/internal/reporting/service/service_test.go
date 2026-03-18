package service

import (
	"testing"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"

	"github.com/athena-lms/go-services/internal/reporting/model"
)

func TestCategorize(t *testing.T) {
	tests := []struct {
		eventType string
		expected  model.EventCategory
	}{
		{"loan.application.created", model.EventCategoryLoanOrigination},
		{"loan.application.approved", model.EventCategoryLoanOrigination},
		{"loan.disbursed", model.EventCategoryLoanManagement},
		{"loan.closed", model.EventCategoryLoanManagement},
		{"payment.completed", model.EventCategoryPayment},
		{"payment.failed", model.EventCategoryPayment},
		{"float.deposited", model.EventCategoryFloat},
		{"aml.alert.created", model.EventCategoryCompliance},
		{"customer.kyc.verified", model.EventCategoryCompliance},
		{"account.created", model.EventCategoryAccount},
		{"something.random", model.EventCategoryUnknown},
		{"", model.EventCategoryUnknown},
	}

	for _, tt := range tests {
		t.Run(tt.eventType, func(t *testing.T) {
			result := categorize(tt.eventType)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestResolveSubjectID(t *testing.T) {
	// Prefers loanId
	payload := map[string]interface{}{
		"loanId":    "loan-123",
		"accountId": "acc-456",
		"paymentId": "pay-789",
	}
	result := resolveSubjectID(payload)
	assert.NotNil(t, result)
	assert.Equal(t, "loan-123", *result)

	// Falls back to accountId
	payload2 := map[string]interface{}{
		"accountId": "acc-456",
		"paymentId": "pay-789",
	}
	result2 := resolveSubjectID(payload2)
	assert.NotNil(t, result2)
	assert.Equal(t, "acc-456", *result2)

	// Falls back to paymentId
	payload3 := map[string]interface{}{
		"paymentId": "pay-789",
	}
	result3 := resolveSubjectID(payload3)
	assert.NotNil(t, result3)
	assert.Equal(t, "pay-789", *result3)

	// Returns nil when nothing present
	payload4 := map[string]interface{}{}
	result4 := resolveSubjectID(payload4)
	assert.Nil(t, result4)

	// Returns nil for nil payload
	result5 := resolveSubjectID(nil)
	assert.Nil(t, result5)
}

func TestExtractString(t *testing.T) {
	payload := map[string]interface{}{
		"key1": "value1",
		"key2": 42,
		"key3": nil,
		"key4": "",
	}

	// String value
	v := extractString(payload, "key1")
	assert.NotNil(t, v)
	assert.Equal(t, "value1", *v)

	// Numeric value converted to string
	v2 := extractString(payload, "key2")
	assert.NotNil(t, v2)
	assert.Equal(t, "42", *v2)

	// Nil value
	v3 := extractString(payload, "key3")
	assert.Nil(t, v3)

	// Empty string returns nil
	v4 := extractString(payload, "key4")
	assert.Nil(t, v4)

	// Missing key
	v5 := extractString(payload, "nonexistent")
	assert.Nil(t, v5)

	// Nil payload
	v6 := extractString(nil, "key1")
	assert.Nil(t, v6)
}

func TestExtractInt64(t *testing.T) {
	payload := map[string]interface{}{
		"num":    float64(42),
		"notNum": "hello",
		"nilVal": nil,
	}

	v := extractInt64(payload, "num")
	assert.NotNil(t, v)
	assert.Equal(t, int64(42), *v)

	v2 := extractInt64(payload, "notNum")
	assert.Nil(t, v2)

	v3 := extractInt64(payload, "nilVal")
	assert.Nil(t, v3)

	v4 := extractInt64(payload, "missing")
	assert.Nil(t, v4)

	v5 := extractInt64(nil, "num")
	assert.Nil(t, v5)
}

func TestExtractDecimal(t *testing.T) {
	payload := map[string]interface{}{
		"amount":  float64(100.50),
		"strAmt":  "250.75",
		"notNum":  "hello",
		"nilVal":  nil,
	}

	v := extractDecimal(payload, "amount")
	assert.NotNil(t, v)
	assert.True(t, decimal.NewFromFloat(100.50).Equal(*v))

	v2 := extractDecimal(payload, "strAmt")
	assert.NotNil(t, v2)
	expected, _ := decimal.NewFromString("250.75")
	assert.True(t, expected.Equal(*v2))

	v3 := extractDecimal(payload, "notNum")
	assert.Nil(t, v3)

	v4 := extractDecimal(payload, "nilVal")
	assert.Nil(t, v4)

	v5 := extractDecimal(nil, "amount")
	assert.Nil(t, v5)
}

func TestCountEventType(t *testing.T) {
	metrics := []*model.EventMetric{
		{EventType: "loan.disbursed", EventCount: 5},
		{EventType: "loan.disbursed", EventCount: 3},
		{EventType: "payment.completed", EventCount: 10},
	}

	assert.Equal(t, 8, countEventType(metrics, "loan.disbursed"))
	assert.Equal(t, 10, countEventType(metrics, "payment.completed"))
	assert.Equal(t, 0, countEventType(metrics, "nonexistent"))
	assert.Equal(t, 0, countEventType(nil, "loan.disbursed"))
}

func TestSumEventType(t *testing.T) {
	metrics := []*model.EventMetric{
		{EventType: "loan.disbursed", TotalAmount: decimal.NewFromFloat(1000.00)},
		{EventType: "loan.disbursed", TotalAmount: decimal.NewFromFloat(2000.50)},
		{EventType: "payment.completed", TotalAmount: decimal.NewFromFloat(500.00)},
	}

	sum := sumEventType(metrics, "loan.disbursed")
	expected, _ := decimal.NewFromString("3000.50")
	assert.True(t, expected.Equal(sum))

	sum2 := sumEventType(metrics, "payment.completed")
	assert.True(t, decimal.NewFromFloat(500.00).Equal(sum2))

	sum3 := sumEventType(metrics, "nonexistent")
	assert.True(t, decimal.Zero.Equal(sum3))
}

func TestStrPtr(t *testing.T) {
	p := strPtr("hello")
	assert.NotNil(t, p)
	assert.Equal(t, "hello", *p)
}
