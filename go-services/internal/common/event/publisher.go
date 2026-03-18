package event

import (
	"context"
	"encoding/json"
	"fmt"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/rabbitmq"
)

// Publisher publishes domain events to the LMS exchange.
type Publisher struct {
	ch     *amqp.Channel
	logger *zap.Logger
}

// NewPublisher creates a new event publisher.
func NewPublisher(conn *rabbitmq.Connection, logger *zap.Logger) (*Publisher, error) {
	ch, err := conn.Channel()
	if err != nil {
		return nil, fmt.Errorf("open publisher channel: %w", err)
	}

	// Enable publisher confirms for reliability
	if err := ch.Confirm(false); err != nil {
		return nil, fmt.Errorf("enable confirms: %w", err)
	}

	return &Publisher{ch: ch, logger: logger}, nil
}

// Publish publishes a DomainEvent to the LMS exchange with its type as routing key.
func (p *Publisher) Publish(ctx context.Context, event *DomainEvent) error {
	body, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal event: %w", err)
	}

	err = p.ch.PublishWithContext(ctx,
		rabbitmq.LMSExchange, // exchange
		event.Type,           // routing key = event type
		false,                // mandatory
		false,                // immediate
		amqp.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp.Persistent,
			Body:         body,
		},
	)
	if err != nil {
		return fmt.Errorf("publish event %s: %w", event.Type, err)
	}

	p.logger.Debug("Published event",
		zap.String("type", event.Type),
		zap.String("id", event.ID),
		zap.String("source", event.Source),
	)

	return nil
}

// Close closes the publisher channel.
func (p *Publisher) Close() error {
	return p.ch.Close()
}
