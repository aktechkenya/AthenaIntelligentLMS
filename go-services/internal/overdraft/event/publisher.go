package event

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/event"
)

const source = "overdraft-service"

// Publisher publishes overdraft domain events to RabbitMQ.
type Publisher struct {
	pub    *event.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new overdraft event publisher.
func NewPublisher(pub *event.Publisher, logger *zap.Logger) *Publisher {
	return &Publisher{pub: pub, logger: logger}
}

func (p *Publisher) publish(ctx context.Context, eventType, tenantID string, payload map[string]interface{}) {
	evt, err := event.NewDomainEvent(eventType, source, tenantID, "", payload)
	if err != nil {
		p.logger.Error("Failed to create event", zap.String("type", eventType), zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish event", zap.String("type", eventType), zap.Error(err))
	} else {
		p.logger.Info("Published event", zap.String("type", eventType))
	}
}

// PublishOverdraftApplied publishes an overdraft.applied event.
func (p *Publisher) PublishOverdraftApplied(ctx context.Context, walletID uuid.UUID, customerID, band string, limit decimal.Decimal, tenantID string) {
	p.publish(ctx, "overdraft.applied", tenantID, map[string]interface{}{
		"walletId":      walletID.String(),
		"customerId":    customerID,
		"creditBand":    band,
		"approvedLimit": limit,
		"tenantId":      tenantID,
	})
}

// PublishOverdraftDrawn publishes an overdraft.drawn event.
func (p *Publisher) PublishOverdraftDrawn(ctx context.Context, walletID uuid.UUID, customerID string, amount decimal.Decimal, tenantID string) {
	p.publish(ctx, "overdraft.drawn", tenantID, map[string]interface{}{
		"walletId":   walletID.String(),
		"customerId": customerID,
		"amount":     amount,
		"tenantId":   tenantID,
	})
}

// PublishOverdraftRepaidDetailed publishes an overdraft.repaid event with breakdown.
func (p *Publisher) PublishOverdraftRepaidDetailed(ctx context.Context, walletID uuid.UUID, customerID string,
	totalAmount, interestRepaid, principalRepaid, feesRepaid decimal.Decimal, tenantID string) {
	p.publish(ctx, "overdraft.repaid", tenantID, map[string]interface{}{
		"walletId":        walletID.String(),
		"customerId":      customerID,
		"amount":          totalAmount,
		"interestRepaid":  interestRepaid,
		"principalRepaid": principalRepaid,
		"feesRepaid":      feesRepaid,
		"tenantId":        tenantID,
	})
}

// PublishInterestCharged publishes an overdraft.interest.charged event.
func (p *Publisher) PublishInterestCharged(ctx context.Context, walletID uuid.UUID, customerID string, interest decimal.Decimal, tenantID string) {
	p.publish(ctx, "overdraft.interest.charged", tenantID, map[string]interface{}{
		"walletId":        walletID.String(),
		"customerId":      customerID,
		"interestCharged": interest,
		"tenantId":        tenantID,
	})
}

// PublishOverdraftSuspended publishes an overdraft.suspended event.
func (p *Publisher) PublishOverdraftSuspended(ctx context.Context, walletID uuid.UUID, customerID, tenantID string) {
	p.publish(ctx, "overdraft.suspended", tenantID, map[string]interface{}{
		"walletId":   walletID.String(),
		"customerId": customerID,
		"tenantId":   tenantID,
	})
}

// PublishFeeCharged publishes an overdraft.fee.charged event.
func (p *Publisher) PublishFeeCharged(ctx context.Context, walletID uuid.UUID, customerID, feeType string, amount decimal.Decimal, reference, tenantID string) {
	p.publish(ctx, "overdraft.fee.charged", tenantID, map[string]interface{}{
		"walletId":   walletID.String(),
		"customerId": customerID,
		"feeType":    feeType,
		"amount":     amount,
		"reference":  reference,
		"tenantId":   tenantID,
	})
}

// PublishBillingStatement publishes an overdraft.billing.statement event.
func (p *Publisher) PublishBillingStatement(ctx context.Context, walletID uuid.UUID, customerID string,
	closingBalance, minimumPayment decimal.Decimal, dueDate time.Time, tenantID string) {
	p.publish(ctx, "overdraft.billing.statement", tenantID, map[string]interface{}{
		"walletId":       walletID.String(),
		"customerId":     customerID,
		"closingBalance": closingBalance,
		"minimumPayment": minimumPayment,
		"dueDate":        dueDate.Format("2006-01-02"),
		"tenantId":       tenantID,
	})
}

// PublishDpdUpdated publishes an overdraft.dpd.updated event.
func (p *Publisher) PublishDpdUpdated(ctx context.Context, walletID uuid.UUID, customerID string, dpd int, stage, tenantID string) {
	p.publish(ctx, "overdraft.dpd.updated", tenantID, map[string]interface{}{
		"walletId":   walletID.String(),
		"customerId": customerID,
		"dpd":        dpd,
		"nplStage":   stage,
		"tenantId":   tenantID,
	})
}

// PublishStageChanged publishes an overdraft.stage.changed event.
func (p *Publisher) PublishStageChanged(ctx context.Context, walletID uuid.UUID, customerID, previousStage, newStage string, dpd int, tenantID string) {
	p.publish(ctx, "overdraft.stage.changed", tenantID, map[string]interface{}{
		"walletId":      walletID.String(),
		"customerId":    customerID,
		"previousStage": previousStage,
		"newStage":      newStage,
		"dpd":           dpd,
		"tenantId":      tenantID,
	})
}
