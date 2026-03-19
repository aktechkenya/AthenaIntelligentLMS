package event

import (
	"context"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/collections/model"
	commonevent "github.com/athena-lms/go-services/internal/common/event"
	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

const serviceName = "collections-service"

// Publisher publishes collections domain events.
type Publisher struct {
	pub    *commonevent.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new collections event publisher.
func NewPublisher(pub *commonevent.Publisher, logger *zap.Logger) *Publisher {
	return &Publisher{pub: pub, logger: logger}
}

// PublishCaseCreated publishes a collection.case.created event.
func (p *Publisher) PublishCaseCreated(ctx context.Context, caseID, loanID uuid.UUID, tenantID string) {
	evt, err := commonevent.NewDomainEvent(
		"collection.case.created", serviceName, tenantID, "",
		map[string]string{"caseId": caseID.String(), "loanId": loanID.String()},
	)
	if err != nil {
		p.logger.Error("Failed to create case created event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish case created event", zap.Error(err))
	} else {
		p.logger.Info("Published collection.case.created", zap.String("caseId", caseID.String()), zap.String("loanId", loanID.String()))
	}
}

// PublishCaseEscalated publishes a collection.case.escalated event.
func (p *Publisher) PublishCaseEscalated(ctx context.Context, caseID, loanID uuid.UUID, newStage model.CollectionStage, tenantID string) {
	evt, err := commonevent.NewDomainEvent(
		"collection.case.escalated", serviceName, tenantID, "",
		map[string]string{"caseId": caseID.String(), "loanId": loanID.String(), "newStage": string(newStage)},
	)
	if err != nil {
		p.logger.Error("Failed to create case escalated event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish case escalated event", zap.Error(err))
	} else {
		p.logger.Info("Published collection.case.escalated", zap.String("caseId", caseID.String()), zap.String("newStage", string(newStage)))
	}
}

// PublishCaseClosed publishes a collection.case.closed event.
func (p *Publisher) PublishCaseClosed(ctx context.Context, caseID, loanID uuid.UUID, tenantID string) {
	evt, err := commonevent.NewDomainEvent(
		"collection.case.closed", serviceName, tenantID, "",
		map[string]string{"caseId": caseID.String(), "loanId": loanID.String()},
	)
	if err != nil {
		p.logger.Error("Failed to create case closed event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish case closed event", zap.Error(err))
	} else {
		p.logger.Info("Published collection.case.closed", zap.String("caseId", caseID.String()), zap.String("loanId", loanID.String()))
	}
}

// PublishWriteOffApproved publishes a collection.writeoff.approved event.
func (p *Publisher) PublishWriteOffApproved(ctx context.Context, caseID, loanID uuid.UUID, amount decimal.Decimal, tenantID string) {
	evt, err := commonevent.NewDomainEvent(
		"collection.writeoff.approved", serviceName, tenantID, "",
		map[string]string{
			"caseId": caseID.String(),
			"loanId": loanID.String(),
			"amount": amount.String(),
		},
	)
	if err != nil {
		p.logger.Error("Failed to create writeoff approved event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish writeoff approved event", zap.Error(err))
	} else {
		p.logger.Info("Published collection.writeoff.approved",
			zap.String("caseId", caseID.String()),
			zap.String("loanId", loanID.String()),
			zap.String("amount", amount.String()),
		)
	}
}

// PublishRestructureRequested publishes a collection.restructure.requested event.
func (p *Publisher) PublishRestructureRequested(ctx context.Context, caseID, loanID uuid.UUID, tenantID string) {
	evt, err := commonevent.NewDomainEvent(
		"collection.restructure.requested", serviceName, tenantID, "",
		map[string]string{"caseId": caseID.String(), "loanId": loanID.String()},
	)
	if err != nil {
		p.logger.Error("Failed to create restructure requested event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish restructure requested event", zap.Error(err))
	} else {
		p.logger.Info("Published collection.restructure.requested",
			zap.String("caseId", caseID.String()),
			zap.String("loanId", loanID.String()),
		)
	}
}

// PublishActionTaken publishes a collection.action.taken event.
func (p *Publisher) PublishActionTaken(ctx context.Context, caseID uuid.UUID, actionType model.ActionType, tenantID string) {
	evt, err := commonevent.NewDomainEvent(
		"collection.action.taken", serviceName, tenantID, "",
		map[string]string{"caseId": caseID.String(), "actionType": string(actionType)},
	)
	if err != nil {
		p.logger.Error("Failed to create action taken event", zap.Error(err))
		return
	}
	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish action taken event", zap.Error(err))
	} else {
		p.logger.Debug("Published collection.action.taken", zap.String("caseId", caseID.String()), zap.String("actionType", string(actionType)))
	}
}
