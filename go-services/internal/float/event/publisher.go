package event

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	commonEvent "github.com/athena-lms/go-services/internal/common/event"
)

const serviceName = "float-service"

// Publisher publishes float domain events.
type Publisher struct {
	pub    *commonEvent.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new float event publisher.
func NewPublisher(pub *commonEvent.Publisher, logger *zap.Logger) *Publisher {
	return &Publisher{pub: pub, logger: logger}
}

// PublishFloatDrawn publishes a float.drawn event.
func (p *Publisher) PublishFloatDrawn(ctx context.Context, accountID uuid.UUID, amount decimal.Decimal, loanID *uuid.UUID, tenantID string) {
	loanStr := ""
	if loanID != nil {
		loanStr = loanID.String()
	}

	payload := map[string]any{
		"floatAccountId": accountID.String(),
		"amount":         amount,
		"loanId":         loanStr,
		"tenantId":       tenantID,
	}

	evt, err := commonEvent.NewDomainEvent(commonEvent.FloatDrawn, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create float.drawn event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish float.drawn event", zap.Error(err))
	}
	p.logger.Info("Published float.drawn event",
		zap.String("accountId", accountID.String()),
		zap.String("amount", amount.String()))
}

// PublishFloatRepaid publishes a float.repaid event.
func (p *Publisher) PublishFloatRepaid(ctx context.Context, accountID uuid.UUID, amount decimal.Decimal, loanID *uuid.UUID, tenantID string) {
	loanStr := ""
	if loanID != nil {
		loanStr = loanID.String()
	}

	payload := map[string]any{
		"floatAccountId": accountID.String(),
		"amount":         amount,
		"loanId":         loanStr,
		"tenantId":       tenantID,
	}

	evt, err := commonEvent.NewDomainEvent(commonEvent.FloatRepaid, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create float.repaid event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish float.repaid event", zap.Error(err))
	}
	p.logger.Info("Published float.repaid event",
		zap.String("accountId", accountID.String()),
		zap.String("amount", amount.String()))
}

// PublishFeeCharged publishes a float.fee.charged event.
func (p *Publisher) PublishFeeCharged(ctx context.Context, accountID uuid.UUID, fee decimal.Decimal, tenantID string) {
	payload := map[string]any{
		"floatAccountId": accountID.String(),
		"fee":            fee,
		"tenantId":       tenantID,
	}

	evt, err := commonEvent.NewDomainEvent(commonEvent.FloatFeeCharged, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create float.fee.charged event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish float.fee.charged event", zap.Error(err))
	}
	p.logger.Info("Published float.fee.charged event",
		zap.String("accountId", accountID.String()),
		zap.String("fee", fee.String()))
}

// PublishLimitChanged publishes a float.limit.changed event.
func (p *Publisher) PublishLimitChanged(ctx context.Context, accountID uuid.UUID, oldLimit, newLimit decimal.Decimal, tenantID string) {
	payload := map[string]any{
		"floatAccountId": accountID.String(),
		"oldLimit":       oldLimit,
		"newLimit":       newLimit,
		"tenantId":       tenantID,
	}

	evt, err := commonEvent.NewDomainEvent(commonEvent.FloatLimitChanged, serviceName, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create float.limit.changed event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish float.limit.changed event", zap.Error(err))
	}
	p.logger.Info("Published float.limit.changed event",
		zap.String("accountId", accountID.String()),
		zap.String("oldLimit", oldLimit.String()),
		zap.String("newLimit", newLimit.String()))
}

// FormatLoanID is a helper to convert a *uuid.UUID to string for event payloads.
func FormatLoanID(loanID *uuid.UUID) string {
	if loanID == nil {
		return ""
	}
	return fmt.Sprintf("%s", loanID.String())
}
