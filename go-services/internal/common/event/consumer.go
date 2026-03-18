package event

import (
	"context"
	"encoding/json"
	"fmt"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/rabbitmq"
)

// Handler processes a domain event. Return nil to ack, error to nack+requeue.
type Handler func(ctx context.Context, event *DomainEvent) error

// Consumer reads domain events from a queue using a worker pool.
// Matches Java's concurrency="3-5" by running N goroutines with prefetchCount.
type Consumer struct {
	conn         *rabbitmq.Connection
	queue        string
	workers      int
	prefetchCount int
	handler      Handler
	logger       *zap.Logger
}

// NewConsumer creates a new event consumer.
// workers: number of goroutines processing messages (equivalent to Java concurrency min).
// prefetchCount: AMQP prefetch (equivalent to Java concurrency max).
func NewConsumer(conn *rabbitmq.Connection, queue string, workers, prefetchCount int, handler Handler, logger *zap.Logger) *Consumer {
	return &Consumer{
		conn:         conn,
		queue:        queue,
		workers:      workers,
		prefetchCount: prefetchCount,
		handler:      handler,
		logger:       logger,
	}
}

// Start begins consuming messages. Blocks until ctx is cancelled.
func (c *Consumer) Start(ctx context.Context) error {
	ch, err := c.conn.Channel()
	if err != nil {
		return fmt.Errorf("open consumer channel: %w", err)
	}
	defer ch.Close()

	if err := ch.Qos(c.prefetchCount, 0, false); err != nil {
		return fmt.Errorf("set qos: %w", err)
	}

	deliveries, err := ch.Consume(
		c.queue,
		"",    // consumer tag (auto-generated)
		false, // autoAck
		false, // exclusive
		false, // noLocal
		false, // noWait
		nil,   // args
	)
	if err != nil {
		return fmt.Errorf("consume queue %s: %w", c.queue, err)
	}

	c.logger.Info("Consumer started",
		zap.String("queue", c.queue),
		zap.Int("workers", c.workers),
		zap.Int("prefetch", c.prefetchCount),
	)

	// Fan out deliveries to worker goroutines
	work := make(chan amqp.Delivery, c.prefetchCount)

	for i := 0; i < c.workers; i++ {
		go c.worker(ctx, i, work)
	}

	for {
		select {
		case <-ctx.Done():
			close(work)
			c.logger.Info("Consumer stopping", zap.String("queue", c.queue))
			return nil
		case d, ok := <-deliveries:
			if !ok {
				close(work)
				return fmt.Errorf("delivery channel closed for queue %s", c.queue)
			}
			work <- d
		}
	}
}

func (c *Consumer) worker(ctx context.Context, id int, work <-chan amqp.Delivery) {
	for d := range work {
		var event DomainEvent
		if err := json.Unmarshal(d.Body, &event); err != nil {
			c.logger.Error("Failed to unmarshal event",
				zap.Int("worker", id),
				zap.Error(err),
			)
			d.Nack(false, false) // don't requeue malformed messages
			continue
		}

		if err := c.handler(ctx, &event); err != nil {
			c.logger.Error("Failed to handle event",
				zap.String("type", event.Type),
				zap.String("id", event.ID),
				zap.Int("worker", id),
				zap.Error(err),
			)
			d.Nack(false, true) // requeue for retry
			continue
		}

		d.Ack(false)
	}
}
