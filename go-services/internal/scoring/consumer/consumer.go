package consumer

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"strconv"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/rabbitmq"
	"github.com/athena-lms/go-services/internal/scoring/service"
)

const (
	// ScoringInboundQueue is the queue name for scoring events.
	// Matches Java RabbitMQConfig.SCORING_INBOUND_QUEUE.
	ScoringInboundQueue = "athena.lms.scoring.inbound.queue"
)

// Consumer listens on the scoring inbound queue for loan application events
// and triggers the scoring pipeline.
type Consumer struct {
	svc    *service.Service
	conn   *rabbitmq.Connection
	logger *zap.Logger
}

// New creates a new Consumer.
func New(svc *service.Service, conn *rabbitmq.Connection, logger *zap.Logger) *Consumer {
	return &Consumer{
		svc:    svc,
		conn:   conn,
		logger: logger,
	}
}

// Start begins consuming messages. Blocks until ctx is cancelled.
func (c *Consumer) Start(ctx context.Context) error {
	// Declare the queue and bindings if not already declared by topology
	ch, err := c.conn.Channel()
	if err != nil {
		return fmt.Errorf("open channel for scoring queue setup: %w", err)
	}
	// Declare the queue
	if _, err := ch.QueueDeclare(ScoringInboundQueue, true, false, false, false, nil); err != nil {
		ch.Close()
		return fmt.Errorf("declare scoring inbound queue: %w", err)
	}
	// Bind to loan.application.submitted and loan.application.approved
	for _, key := range []string{"loan.application.submitted", "loan.application.approved"} {
		if err := ch.QueueBind(ScoringInboundQueue, key, rabbitmq.LMSExchange, false, nil); err != nil {
			ch.Close()
			return fmt.Errorf("bind scoring queue to %s: %w", key, err)
		}
	}
	ch.Close()

	consumer := event.NewConsumer(c.conn, ScoringInboundQueue, 3, 5, c.handleEvent, c.logger)
	return consumer.Start(ctx)
}

// handleEvent processes a single domain event from the scoring queue.
func (c *Consumer) handleEvent(ctx context.Context, evt *event.DomainEvent) error {
	// Extract payload fields
	var payload map[string]any
	if err := json.Unmarshal(evt.Payload, &payload); err != nil {
		c.logger.Error("Failed to unmarshal scoring event payload", zap.Error(err))
		return nil // don't requeue malformed messages
	}

	eventType := evt.Type
	if eventType == "" {
		if t, ok := payload["type"].(string); ok {
			eventType = t
		} else if t, ok := payload["eventType"].(string); ok {
			eventType = t
		} else {
			eventType = "UNKNOWN"
		}
	}

	loanApplicationID := resolveLoanApplicationID(payload)
	if loanApplicationID == "" {
		c.logger.Warn("Could not resolve loanApplicationId from event payload, skipping",
			zap.String("eventType", eventType))
		return nil
	}

	customerID := resolveCustomerID(payload)
	if customerID == 0 {
		c.logger.Warn("Missing customerId in event payload, skipping",
			zap.String("loanApplicationId", loanApplicationID))
		return nil
	}

	tenantID := evt.TenantID
	if tenantID == "" {
		if t, ok := payload["tenantId"].(string); ok && t != "" {
			tenantID = t
		} else {
			tenantID = "default"
		}
	}

	c.logger.Info("Received loan event for scoring",
		zap.String("type", eventType),
		zap.String("loanApplicationId", loanApplicationID),
		zap.Int64("customerId", customerID),
		zap.String("tenantId", tenantID),
	)

	c.svc.TriggerScoring(ctx, loanApplicationID, customerID, eventType, tenantID)
	return nil
}

// resolveLoanApplicationID tries multiple field names for the loan application ID.
func resolveLoanApplicationID(payload map[string]any) string {
	for _, key := range []string{"applicationId", "loanApplicationId", "id"} {
		if val, ok := payload[key]; ok && val != nil {
			s := fmt.Sprintf("%v", val)
			if s != "" && s != "<nil>" {
				return s
			}
		}
	}
	return ""
}

// resolveCustomerID extracts and coerces the customer ID from the payload.
func resolveCustomerID(payload map[string]any) int64 {
	val, ok := payload["customerId"]
	if !ok || val == nil {
		return 0
	}

	switch v := val.(type) {
	case float64:
		return int64(v)
	case json.Number:
		n, err := v.Int64()
		if err == nil {
			return n
		}
	case string:
		n, err := strconv.ParseInt(v, 10, 64)
		if err == nil {
			return n
		}
		// Non-numeric string: hash it
		return int64(math.Abs(float64(hashString(v))))
	}

	// Fallback: try string conversion + hash
	s := fmt.Sprintf("%v", val)
	n, err := strconv.ParseInt(s, 10, 64)
	if err == nil {
		return n
	}
	return int64(math.Abs(float64(hashString(s))))
}

func hashString(s string) int {
	h := 0
	for _, c := range s {
		h = h*31 + int(c)
	}
	if h < 0 {
		h = -h
	}
	return h
}
