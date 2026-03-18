package consumer

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	commonEvent "github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/rabbitmq"
	"github.com/athena-lms/go-services/internal/management/service"
)

// LoanDisbursedPayload is the event payload for loan.disbursed events.
type LoanDisbursedPayload struct {
	ApplicationID      string `json:"applicationId"`
	CustomerID         string `json:"customerId"`
	ProductID          string `json:"productId"`
	TenantID           string `json:"tenantId"`
	Amount             string `json:"amount"`
	InterestRate       string `json:"interestRate"`
	TenorMonths        int    `json:"tenorMonths"`
	ScheduleType       string `json:"scheduleType"`
	RepaymentFrequency string `json:"repaymentFrequency"`
}

// LoanDisbursedConsumer consumes loan.disbursed events and activates loans.
type LoanDisbursedConsumer struct {
	consumer *commonEvent.Consumer
	svc      *service.Service
	logger   *zap.Logger
}

// NewLoanDisbursedConsumer creates a consumer for the loan management queue.
func NewLoanDisbursedConsumer(conn *rabbitmq.Connection, svc *service.Service, logger *zap.Logger) *LoanDisbursedConsumer {
	c := &LoanDisbursedConsumer{
		svc:    svc,
		logger: logger,
	}
	c.consumer = commonEvent.NewConsumer(conn, rabbitmq.LoanMgmtQueue, 3, 5, c.handle, logger)
	return c
}

// Start begins consuming messages. Blocks until ctx is cancelled.
func (c *LoanDisbursedConsumer) Start(ctx context.Context) error {
	return c.consumer.Start(ctx)
}

func (c *LoanDisbursedConsumer) handle(ctx context.Context, evt *commonEvent.DomainEvent) error {
	if evt.Type != commonEvent.LoanDisbursed {
		c.logger.Debug("Ignoring event type", zap.String("type", evt.Type))
		return nil
	}

	c.logger.Info("Received loan.disbursed event", zap.String("id", evt.ID))

	var payload LoanDisbursedPayload
	if err := evt.UnmarshalPayload(&payload); err != nil {
		c.logger.Error("Failed to unmarshal loan.disbursed payload", zap.Error(err))
		return nil // don't retry malformed messages
	}

	// Use tenant from envelope if payload doesn't have it
	tenantID := payload.TenantID
	if tenantID == "" {
		tenantID = evt.TenantID
	}

	applicationID, err := uuid.Parse(payload.ApplicationID)
	if err != nil {
		c.logger.Error("Invalid applicationId", zap.String("value", payload.ApplicationID), zap.Error(err))
		return nil
	}

	productID, err := uuid.Parse(payload.ProductID)
	if err != nil {
		c.logger.Error("Invalid productId", zap.String("value", payload.ProductID), zap.Error(err))
		return nil
	}

	amount, err := decimal.NewFromString(payload.Amount)
	if err != nil {
		c.logger.Error("Invalid amount", zap.String("value", payload.Amount), zap.Error(err))
		return nil
	}

	interestRate := decimal.Zero
	if payload.InterestRate != "" {
		rate, err := decimal.NewFromString(payload.InterestRate)
		if err != nil {
			c.logger.Warn("Invalid interestRate, defaulting to 0", zap.String("value", payload.InterestRate))
		} else {
			interestRate = rate
		}
	}

	tenorMonths := payload.TenorMonths
	if tenorMonths <= 0 {
		tenorMonths = 12
	}

	if err := c.svc.ActivateLoan(ctx, applicationID, payload.CustomerID, productID, tenantID,
		amount, interestRate, tenorMonths, payload.ScheduleType, payload.RepaymentFrequency); err != nil {
		return fmt.Errorf("activate loan: %w", err)
	}

	return nil
}
