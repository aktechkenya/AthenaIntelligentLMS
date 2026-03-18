package rabbitmq

import (
	"fmt"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.uber.org/zap"
)

// Connection wraps an AMQP connection with reconnection support.
type Connection struct {
	conn   *amqp.Connection
	url    string
	logger *zap.Logger
}

// NewConnection creates and dials an AMQP connection with retry.
func NewConnection(url string, logger *zap.Logger) (*Connection, error) {
	c := &Connection{url: url, logger: logger}
	if err := c.connect(); err != nil {
		return nil, err
	}
	return c, nil
}

func (c *Connection) connect() error {
	var err error
	for i := 0; i < 30; i++ {
		c.conn, err = amqp.Dial(c.url)
		if err == nil {
			c.logger.Info("Connected to RabbitMQ")
			return nil
		}
		c.logger.Warn("RabbitMQ connection attempt failed, retrying...",
			zap.Int("attempt", i+1),
			zap.Error(err))
		time.Sleep(2 * time.Second)
	}
	return fmt.Errorf("failed to connect to RabbitMQ after 30 attempts: %w", err)
}

// Channel opens a new AMQP channel.
func (c *Connection) Channel() (*amqp.Channel, error) {
	return c.conn.Channel()
}

// Close closes the connection.
func (c *Connection) Close() error {
	if c.conn != nil {
		return c.conn.Close()
	}
	return nil
}
