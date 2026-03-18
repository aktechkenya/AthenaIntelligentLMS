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

// TryConnection attempts to connect but returns a Connection even on failure.
// The connection can be retried later. This allows HTTP to start immediately.
func TryConnection(url string, logger *zap.Logger) *Connection {
	c := &Connection{url: url, logger: logger}
	if err := c.connect(); err != nil {
		logger.Warn("RabbitMQ not available at startup, will retry", zap.Error(err))
	}
	return c
}

// IsConnected returns true if the connection is established.
func (c *Connection) IsConnected() bool {
	return c.conn != nil && !c.conn.IsClosed()
}

// Reconnect attempts to establish the connection again.
func (c *Connection) Reconnect() error {
	return c.connect()
}

func (c *Connection) connect() error {
	var err error
	for i := 0; i < 10; i++ {
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
	return fmt.Errorf("failed to connect to RabbitMQ after 10 attempts: %w", err)
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
