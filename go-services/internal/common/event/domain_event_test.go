package event

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewDomainEvent(t *testing.T) {
	payload := map[string]any{
		"loanId":     123,
		"customerId": 456,
		"amount":     1000.50,
	}

	evt, err := NewDomainEvent(LoanDisbursed, "loan-management-service", "tenant1", "req-123", payload)
	require.NoError(t, err)

	assert.NotEmpty(t, evt.ID)
	assert.Equal(t, LoanDisbursed, evt.Type)
	assert.Equal(t, 1, evt.Version)
	assert.Equal(t, "loan-management-service", evt.Source)
	assert.Equal(t, "tenant1", evt.TenantID)
	assert.Equal(t, "req-123", evt.CorrelationID)
	assert.NotEmpty(t, evt.Timestamp)
	assert.NotEmpty(t, evt.Payload)
}

func TestDomainEventJSONCompatibility(t *testing.T) {
	// Verify JSON shape matches Java DomainEvent exactly
	evt, err := NewDomainEvent(PaymentCompleted, "payment-service", "tenant1", "corr-1", map[string]string{"status": "ok"})
	require.NoError(t, err)

	b, err := json.Marshal(evt)
	require.NoError(t, err)

	var m map[string]any
	err = json.Unmarshal(b, &m)
	require.NoError(t, err)

	// Verify all expected fields are present with correct names (camelCase)
	assert.Contains(t, m, "id")
	assert.Contains(t, m, "type")
	assert.Contains(t, m, "version")
	assert.Contains(t, m, "source")
	assert.Contains(t, m, "tenantId")
	assert.Contains(t, m, "correlationId")
	assert.Contains(t, m, "timestamp")
	assert.Contains(t, m, "payload")

	assert.Equal(t, "payment.completed", m["type"])
	assert.Equal(t, float64(1), m["version"])
}

func TestDomainEventUnmarshalPayload(t *testing.T) {
	type LoanPayload struct {
		LoanID     int     `json:"loanId"`
		CustomerID int     `json:"customerId"`
		Amount     float64 `json:"amount"`
	}

	original := LoanPayload{LoanID: 123, CustomerID: 456, Amount: 1000.50}
	evt, err := NewDomainEvent(LoanDisbursed, "svc", "t1", "", original)
	require.NoError(t, err)

	var decoded LoanPayload
	err = evt.UnmarshalPayload(&decoded)
	require.NoError(t, err)

	assert.Equal(t, original, decoded)
}

func TestDomainEventRoundTripJSON(t *testing.T) {
	// Simulate Java publishing, Go consuming
	javaJSON := `{
		"id": "550e8400-e29b-41d4-a716-446655440000",
		"type": "loan.disbursed",
		"version": 1,
		"source": "loan-management-service",
		"tenantId": "tenant1",
		"correlationId": "req-abc",
		"timestamp": "2024-01-15T10:30:00.123456Z",
		"payload": {"loanId": 42, "amount": 5000}
	}`

	var evt DomainEvent
	err := json.Unmarshal([]byte(javaJSON), &evt)
	require.NoError(t, err)

	assert.Equal(t, "550e8400-e29b-41d4-a716-446655440000", evt.ID)
	assert.Equal(t, LoanDisbursed, evt.Type)
	assert.Equal(t, 1, evt.Version)
	assert.Equal(t, "loan-management-service", evt.Source)
	assert.Equal(t, "tenant1", evt.TenantID)
	assert.Equal(t, "req-abc", evt.CorrelationID)

	var payload map[string]any
	err = evt.UnmarshalPayload(&payload)
	require.NoError(t, err)
	assert.Equal(t, float64(42), payload["loanId"])
}
