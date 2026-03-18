package consumer

import (
	"context"
	"encoding/json"
	"fmt"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/compliance/model"
	"github.com/athena-lms/go-services/internal/compliance/repository"
	"github.com/athena-lms/go-services/internal/compliance/service"
)

// Consumer handles incoming compliance domain events from RabbitMQ.
type Consumer struct {
	svc    *service.Service
	repo   *repository.Repository
	logger *zap.Logger
}

// New creates a new compliance event Consumer.
func New(svc *service.Service, repo *repository.Repository, logger *zap.Logger) *Consumer {
	return &Consumer{svc: svc, repo: repo, logger: logger}
}

// Handle processes a single domain event. This is the event.Handler function
// passed to the common event.Consumer.
func (c *Consumer) Handle(ctx context.Context, evt *event.DomainEvent) error {
	c.logger.Info("Compliance listener received event", zap.String("type", evt.Type))

	tenantID := evt.TenantID
	if tenantID == "" {
		tenantID = "unknown"
	}

	// Extract subject ID from payload for logging
	subjectID := c.extractSubjectID(evt)
	payloadStr := string(evt.Payload)

	// Log all events to compliance_events table
	if err := c.svc.LogEvent(ctx, evt.Type, evt.Source, subjectID, payloadStr, tenantID); err != nil {
		c.logger.Error("Failed to log compliance event", zap.Error(err))
	}

	// Handle specific event types
	switch evt.Type {
	case event.AMLAlertRaised:
		c.handleAmlAlertRaised(ctx, evt, tenantID)
	case event.CustomerKYCPassed:
		c.handleKycPassed(ctx, evt, tenantID)
	case event.CustomerKYCFailed:
		c.handleKycFailed(ctx, evt, tenantID)
	default:
		c.logger.Debug("No specific handler for event type", zap.String("type", evt.Type))
	}

	return nil
}

func (c *Consumer) handleAmlAlertRaised(ctx context.Context, evt *event.DomainEvent, tenantID string) {
	c.logger.Info("Processing AML alert raised event", zap.String("tenant", tenantID))
	// Alert already stored by the service that raised it; event logged above
}

func (c *Consumer) handleKycPassed(ctx context.Context, evt *event.DomainEvent, tenantID string) {
	var payload struct {
		CustomerID string `json:"customerId"`
	}
	if err := evt.UnmarshalPayload(&payload); err != nil {
		c.logger.Error("Failed to unmarshal KYC passed payload", zap.Error(err))
		return
	}

	if payload.CustomerID == "" {
		return
	}

	rec, err := c.repo.GetKycByTenantAndCustomer(ctx, tenantID, payload.CustomerID)
	if err != nil {
		c.logger.Error("Failed to get KYC record", zap.Error(err))
		return
	}
	if rec == nil {
		return
	}

	now := repository.Now()
	rec.Status = model.KycStatusPassed
	rec.CheckedAt = &now

	if err := c.repo.UpdateKyc(ctx, rec); err != nil {
		c.logger.Error("Failed to update KYC record to PASSED", zap.Error(err))
		return
	}

	c.logger.Info("KYC record updated to PASSED",
		zap.String("customerId", payload.CustomerID),
		zap.String("tenant", tenantID))
}

func (c *Consumer) handleKycFailed(ctx context.Context, evt *event.DomainEvent, tenantID string) {
	var payload struct {
		CustomerID    string `json:"customerId"`
		FailureReason string `json:"failureReason"`
	}
	if err := evt.UnmarshalPayload(&payload); err != nil {
		c.logger.Error("Failed to unmarshal KYC failed payload", zap.Error(err))
		return
	}

	if payload.CustomerID == "" {
		return
	}

	rec, err := c.repo.GetKycByTenantAndCustomer(ctx, tenantID, payload.CustomerID)
	if err != nil {
		c.logger.Error("Failed to get KYC record", zap.Error(err))
		return
	}
	if rec == nil {
		return
	}

	now := repository.Now()
	rec.Status = model.KycStatusFailed
	rec.FailureReason = &payload.FailureReason
	rec.CheckedAt = &now

	if err := c.repo.UpdateKyc(ctx, rec); err != nil {
		c.logger.Error("Failed to update KYC record to FAILED", zap.Error(err))
		return
	}

	c.logger.Info("KYC record updated to FAILED",
		zap.String("customerId", payload.CustomerID),
		zap.String("tenant", tenantID))
}

func (c *Consumer) extractSubjectID(evt *event.DomainEvent) string {
	var payload map[string]json.RawMessage
	if err := json.Unmarshal(evt.Payload, &payload); err != nil {
		return ""
	}

	for _, key := range []string{"alertId", "customerId"} {
		if raw, ok := payload[key]; ok {
			var val string
			if err := json.Unmarshal(raw, &val); err == nil {
				return val
			}
			// Try as number
			var num float64
			if err := json.Unmarshal(raw, &num); err == nil {
				return fmt.Sprintf("%.0f", num)
			}
		}
	}
	return ""
}
