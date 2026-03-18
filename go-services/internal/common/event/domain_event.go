package event

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
)

// DomainEvent is the generic event envelope for all LMS services.
// Published to athena.lms.exchange with routing key = event type.
// JSON shape must be byte-compatible with the Java DomainEvent<T>.
type DomainEvent struct {
	// Unique event ID (UUID).
	ID string `json:"id"`

	// Routing key / event type (e.g. "loan.disbursed").
	Type string `json:"type"`

	// Schema version for forward compatibility.
	Version int `json:"version"`

	// Originating service name.
	Source string `json:"source"`

	// Tenant identifier for multi-tenant routing.
	TenantID string `json:"tenantId"`

	// Correlation ID for distributed tracing.
	CorrelationID string `json:"correlationId,omitempty"`

	// ISO-8601 timestamp — matches Java Instant serialization.
	Timestamp string `json:"timestamp"`

	// Domain-specific payload (raw JSON to preserve type flexibility).
	Payload json.RawMessage `json:"payload"`
}

// NewDomainEvent creates a new DomainEvent with auto-populated ID and timestamp.
func NewDomainEvent(eventType, source, tenantID, correlationID string, payload any) (*DomainEvent, error) {
	payloadBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	return &DomainEvent{
		ID:            uuid.New().String(),
		Type:          eventType,
		Version:       1,
		Source:        source,
		TenantID:      tenantID,
		CorrelationID: correlationID,
		Timestamp:     time.Now().UTC().Format(time.RFC3339Nano),
		Payload:       payloadBytes,
	}, nil
}

// UnmarshalPayload decodes the Payload into the given target.
func (e *DomainEvent) UnmarshalPayload(target any) error {
	return json.Unmarshal(e.Payload, target)
}
