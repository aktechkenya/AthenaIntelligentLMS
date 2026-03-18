package consumer

import (
	"context"
	"crypto/md5"
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	commonEvent "github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/rabbitmq"
	"github.com/athena-lms/go-services/internal/float/service"
)

const (
	// FloatInboundQueue is the local inbound queue for loan.disbursed events.
	FloatInboundQueue = "athena.lms.float.inbound.queue"
)

// Consumer manages the two float event consumers:
//   - floatQueue (account.credit.received) -> processTopUp
//   - floatInboundQueue (loan.disbursed) -> processDraw
type Consumer struct {
	svc    *service.Service
	conn   *rabbitmq.Connection
	logger *zap.Logger
}

// New creates a new float event consumer.
func New(svc *service.Service, conn *rabbitmq.Connection, logger *zap.Logger) *Consumer {
	return &Consumer{svc: svc, conn: conn, logger: logger}
}

// DeclareFloatInboundQueue declares the float inbound queue and binds it to loan.disbursed.
func (c *Consumer) DeclareFloatInboundQueue() error {
	ch, err := c.conn.Channel()
	if err != nil {
		return fmt.Errorf("open channel for float inbound queue: %w", err)
	}
	defer ch.Close()

	// Declare the float inbound queue
	if _, err := ch.QueueDeclare(FloatInboundQueue, true, false, false, false, nil); err != nil {
		return fmt.Errorf("declare float inbound queue: %w", err)
	}

	// Bind to loan.disbursed routing key
	if err := ch.QueueBind(FloatInboundQueue, "loan.disbursed", rabbitmq.LMSExchange, false, nil); err != nil {
		return fmt.Errorf("bind float inbound queue: %w", err)
	}

	c.logger.Info("Declared and bound float inbound queue",
		zap.String("queue", FloatInboundQueue),
		zap.String("routingKey", "loan.disbursed"))
	return nil
}

// StartFloatQueueConsumer starts consuming from the main float queue (account.credit.received).
func (c *Consumer) StartFloatQueueConsumer(ctx context.Context) error {
	consumer := commonEvent.NewConsumer(c.conn, rabbitmq.FloatQueue, 3, 5, c.handleAccountCredit, c.logger)
	return consumer.Start(ctx)
}

// StartInboundQueueConsumer starts consuming from the float inbound queue (loan.disbursed).
func (c *Consumer) StartInboundQueueConsumer(ctx context.Context) error {
	consumer := commonEvent.NewConsumer(c.conn, FloatInboundQueue, 3, 5, c.handleLoanDisbursed, c.logger)
	return consumer.Start(ctx)
}

// handleAccountCredit processes account.credit.received events — tops up the float.
func (c *Consumer) handleAccountCredit(ctx context.Context, event *commonEvent.DomainEvent) error {
	var payload struct {
		TenantID  string          `json:"tenantId"`
		Amount    json.Number     `json:"amount"`
		AccountID string          `json:"accountId"`
	}
	if err := event.UnmarshalPayload(&payload); err != nil {
		c.logger.Error("Failed to unmarshal account.credit.received payload", zap.Error(err))
		return nil // don't retry malformed payloads
	}

	amount, err := decimal.NewFromString(payload.Amount.String())
	if err != nil {
		c.logger.Error("Invalid amount in account.credit.received", zap.String("amount", payload.Amount.String()))
		return nil
	}

	tenantID := payload.TenantID
	if tenantID == "" {
		tenantID = event.TenantID
	}

	c.logger.Info("Received account.credit.received event",
		zap.String("tenantId", tenantID),
		zap.String("amount", amount.String()))

	c.svc.ProcessTopUp(ctx, payload.AccountID, amount, tenantID)
	return nil
}

// handleLoanDisbursed processes loan.disbursed events — draws float and creates an allocation.
func (c *Consumer) handleLoanDisbursed(ctx context.Context, event *commonEvent.DomainEvent) error {
	var payload struct {
		TenantID      string      `json:"tenantId"`
		Amount        json.Number `json:"amount"`
		ApplicationID string      `json:"applicationId"`
		LoanID        string      `json:"loanId"`
	}
	if err := event.UnmarshalPayload(&payload); err != nil {
		c.logger.Error("Failed to unmarshal loan.disbursed payload", zap.Error(err))
		return nil // don't retry malformed payloads
	}

	amount, err := decimal.NewFromString(payload.Amount.String())
	if err != nil {
		c.logger.Error("Invalid amount in loan.disbursed", zap.String("amount", payload.Amount.String()))
		return nil
	}

	tenantID := payload.TenantID
	if tenantID == "" {
		tenantID = event.TenantID
	}

	// Use applicationId as the reference since loanId isn't available at disbursement time
	refID := payload.ApplicationID
	if refID == "" {
		refID = payload.LoanID
	}
	if refID == "" {
		c.logger.Warn("No applicationId or loanId found in loan.disbursed event, skipping float draw")
		return nil
	}

	// Parse as UUID; if not a UUID, use a deterministic UUID from its hash (matches Java UUID.nameUUIDFromBytes)
	loanRef, err := uuid.Parse(refID)
	if err != nil {
		// Mimic Java's UUID.nameUUIDFromBytes: MD5-based UUID v3
		hash := md5.Sum([]byte(refID))
		hash[6] = (hash[6] & 0x0f) | 0x30 // version 3
		hash[8] = (hash[8] & 0x3f) | 0x80 // variant 10
		loanRef, _ = uuid.FromBytes(hash[:])
	}

	c.logger.Info("Received loan.disbursed event",
		zap.String("loanRef", loanRef.String()),
		zap.String("tenantId", tenantID),
		zap.String("amount", amount.String()))

	c.svc.ProcessDraw(ctx, loanRef, amount, tenantID)
	return nil
}
