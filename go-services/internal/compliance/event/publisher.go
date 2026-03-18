package event

import (
	"context"

	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/event"
)

const serviceName = "compliance-service"

// Publisher publishes compliance domain events to RabbitMQ.
type Publisher struct {
	pub    *event.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new compliance event publisher.
func NewPublisher(pub *event.Publisher, logger *zap.Logger) *Publisher {
	return &Publisher{pub: pub, logger: logger}
}

// PublishAmlAlertRaised publishes an aml.alert.raised event.
func (p *Publisher) PublishAmlAlertRaised(ctx context.Context, alertID uuid.UUID, alertType, customerID, tenantID string) {
	payload := map[string]string{
		"alertId":    alertID.String(),
		"alertType":  alertType,
		"customerId": customerID,
	}

	evt, err := event.NewDomainEvent(event.AMLAlertRaised, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create AML alert raised event", zap.Error(err))
		return
	}

	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish AML alert raised event",
			zap.String("alertId", alertID.String()), zap.Error(err))
		return
	}
	p.logger.Info("Published AML alert raised event", zap.String("alertId", alertID.String()))
}

// PublishSarFiled publishes an aml.sar.filed event.
func (p *Publisher) PublishSarFiled(ctx context.Context, alertID uuid.UUID, sarRef, tenantID string) {
	payload := map[string]string{
		"alertId":      alertID.String(),
		"sarReference": sarRef,
	}

	evt, err := event.NewDomainEvent(event.AMLSARFiled, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create SAR filed event", zap.Error(err))
		return
	}

	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish SAR filed event",
			zap.String("alertId", alertID.String()), zap.Error(err))
		return
	}
	p.logger.Info("Published SAR filed event",
		zap.String("alertId", alertID.String()), zap.String("sarRef", sarRef))
}

// PublishKycPassed publishes a customer.kyc.passed event.
func (p *Publisher) PublishKycPassed(ctx context.Context, customerID, tenantID string) {
	payload := map[string]string{
		"customerId": customerID,
	}

	evt, err := event.NewDomainEvent(event.CustomerKYCPassed, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create KYC passed event", zap.Error(err))
		return
	}

	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish KYC passed event",
			zap.String("customerId", customerID), zap.Error(err))
		return
	}
	p.logger.Info("Published KYC passed event", zap.String("customerId", customerID))
}

// PublishKycFailed publishes a customer.kyc.failed event.
func (p *Publisher) PublishKycFailed(ctx context.Context, customerID, failureReason, tenantID string) {
	payload := map[string]string{
		"customerId":    customerID,
		"failureReason": failureReason,
	}

	evt, err := event.NewDomainEvent(event.CustomerKYCFailed, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create KYC failed event", zap.Error(err))
		return
	}

	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish KYC failed event",
			zap.String("customerId", customerID), zap.Error(err))
		return
	}
	p.logger.Info("Published KYC failed event", zap.String("customerId", customerID))
}
