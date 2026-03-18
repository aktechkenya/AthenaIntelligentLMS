package consumer

import (
	"encoding/json"
	"testing"

	"github.com/athena-lms/go-services/internal/common/event"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestExtractSubjectID_AlertID(t *testing.T) {
	c := &Consumer{logger: zap.NewNop()}

	payload, _ := json.Marshal(map[string]string{
		"alertId":   "abc-123",
		"alertType": "LARGE_TRANSACTION",
	})

	evt := &event.DomainEvent{
		Type:    event.AMLAlertRaised,
		Payload: payload,
	}

	subjectID := c.extractSubjectID(evt)
	assert.Equal(t, "abc-123", subjectID)
}

func TestExtractSubjectID_CustomerID(t *testing.T) {
	c := &Consumer{logger: zap.NewNop()}

	payload, _ := json.Marshal(map[string]string{
		"customerId": "cust-456",
	})

	evt := &event.DomainEvent{
		Type:    event.CustomerKYCPassed,
		Payload: payload,
	}

	subjectID := c.extractSubjectID(evt)
	assert.Equal(t, "cust-456", subjectID)
}

func TestExtractSubjectID_Empty(t *testing.T) {
	c := &Consumer{logger: zap.NewNop()}

	payload, _ := json.Marshal(map[string]string{
		"foo": "bar",
	})

	evt := &event.DomainEvent{
		Type:    "some.event",
		Payload: payload,
	}

	subjectID := c.extractSubjectID(evt)
	assert.Equal(t, "", subjectID)
}

func TestExtractSubjectID_InvalidPayload(t *testing.T) {
	c := &Consumer{logger: zap.NewNop()}

	evt := &event.DomainEvent{
		Type:    "some.event",
		Payload: []byte("not json"),
	}

	subjectID := c.extractSubjectID(evt)
	assert.Equal(t, "", subjectID)
}

func TestExtractSubjectID_PrefersAlertID(t *testing.T) {
	c := &Consumer{logger: zap.NewNop()}

	payload, _ := json.Marshal(map[string]string{
		"alertId":    "alert-789",
		"customerId": "cust-456",
	})

	evt := &event.DomainEvent{
		Type:    event.AMLAlertRaised,
		Payload: payload,
	}

	subjectID := c.extractSubjectID(evt)
	assert.Equal(t, "alert-789", subjectID)
}
