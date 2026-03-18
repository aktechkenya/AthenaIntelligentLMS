package event

import (
	"context"

	"go.uber.org/zap"

	commonevent "github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/rabbitmq"
)

const serviceName = "product-service"

// Event types specific to product service.
const (
	ProductCreated   = "product.created"
	ProductUpdated   = "product.updated"
	ProductActivated = "product.activated"
)

// Publisher publishes product-related domain events.
type Publisher struct {
	pub    *commonevent.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new product event publisher.
func NewPublisher(conn *rabbitmq.Connection, logger *zap.Logger) (*Publisher, error) {
	pub, err := commonevent.NewPublisher(conn, logger)
	if err != nil {
		return nil, err
	}
	return &Publisher{pub: pub, logger: logger}, nil
}

// PublishProductCreated publishes a product.created event.
func (p *Publisher) PublishProductCreated(ctx context.Context, tenantID string, payload any) {
	p.publish(ctx, ProductCreated, tenantID, payload)
}

// PublishProductUpdated publishes a product.updated event.
func (p *Publisher) PublishProductUpdated(ctx context.Context, tenantID string, payload any) {
	p.publish(ctx, ProductUpdated, tenantID, payload)
}

// PublishProductActivated publishes a product.activated event.
func (p *Publisher) PublishProductActivated(ctx context.Context, tenantID string, payload any) {
	p.publish(ctx, ProductActivated, tenantID, payload)
}

func (p *Publisher) publish(ctx context.Context, eventType, tenantID string, payload any) {
	evt, err := commonevent.NewDomainEvent(eventType, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create domain event",
			zap.String("type", eventType),
			zap.Error(err),
		)
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish domain event",
			zap.String("type", eventType),
			zap.String("id", evt.ID),
			zap.Error(err),
		)
	}
}

// Close closes the underlying publisher channel.
func (p *Publisher) Close() error {
	return p.pub.Close()
}
