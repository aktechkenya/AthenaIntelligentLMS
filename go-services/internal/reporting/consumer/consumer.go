package consumer

import (
	"context"
	"encoding/json"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/rabbitmq"
	"github.com/athena-lms/go-services/internal/reporting/service"
)

const (
	queueName     = "reporting.queue"
	workers       = 3
	prefetchCount = 5
)

// Start creates and starts the RabbitMQ consumer for reporting events.
// It blocks until the context is cancelled.
func Start(ctx context.Context, conn *rabbitmq.Connection, svc *service.Service, logger *zap.Logger) error {
	handler := func(ctx context.Context, evt *event.DomainEvent) error {
		return handleEvent(ctx, evt, svc, logger)
	}

	c := event.NewConsumer(conn, queueName, workers, prefetchCount, handler, logger)
	return c.Start(ctx)
}

func handleEvent(ctx context.Context, evt *event.DomainEvent, svc *service.Service, logger *zap.Logger) error {
	// Resolve event type from the envelope or payload
	eventType := resolveEventType(evt)
	tenantID := evt.TenantID
	if tenantID == "" {
		tenantID = "default"
	}

	// Unmarshal payload into a generic map
	payload := make(map[string]interface{})
	if evt.Payload != nil {
		if err := json.Unmarshal(evt.Payload, &payload); err != nil {
			logger.Warn("Could not unmarshal event payload, using empty map",
				zap.String("type", eventType),
				zap.Error(err),
			)
		}
	}

	// Inject envelope-level fields into payload for consistency with Java
	payload["tenantId"] = tenantID
	if evt.Source != "" {
		payload["sourceService"] = evt.Source
	}
	if evt.ID != "" {
		payload["eventId"] = evt.ID
	}

	return svc.RecordEvent(ctx, eventType, payload, tenantID)
}

func resolveEventType(evt *event.DomainEvent) string {
	if evt.Type != "" {
		return evt.Type
	}

	// Fallback: check payload for type/eventType fields
	if evt.Payload != nil {
		var m map[string]interface{}
		if json.Unmarshal(evt.Payload, &m) == nil {
			if t, ok := m["type"].(string); ok && t != "" {
				return t
			}
			if t, ok := m["eventType"].(string); ok && t != "" {
				return t
			}
		}
	}

	return "UNKNOWN"
}
