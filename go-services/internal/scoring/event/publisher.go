package event

import (
	"context"

	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	commonevent "github.com/athena-lms/go-services/internal/common/event"
)

// Publisher publishes scoring domain events to the LMS exchange.
type Publisher struct {
	pub    *commonevent.Publisher
	logger *zap.Logger
}

// NewPublisher creates a new scoring event publisher.
func NewPublisher(pub *commonevent.Publisher, logger *zap.Logger) *Publisher {
	return &Publisher{pub: pub, logger: logger}
}

// CreditAssessedPayload is the payload for loan.credit.assessed events.
type CreditAssessedPayload struct {
	LoanApplicationID string          `json:"loanApplicationId"`
	CustomerID        int64           `json:"customerId"`
	FinalScore        decimal.Decimal `json:"finalScore"`
	ScoreBand         string          `json:"scoreBand"`
	PdProbability     decimal.Decimal `json:"pdProbability"`
}

// PublishCreditAssessed publishes a LOAN_CREDIT_ASSESSED event.
func (p *Publisher) PublishCreditAssessed(ctx context.Context,
	loanApplicationID string, customerID int64,
	finalScore decimal.Decimal, scoreBand string,
	pdProbability decimal.Decimal, tenantID string) {

	payload := CreditAssessedPayload{
		LoanApplicationID: loanApplicationID,
		CustomerID:        customerID,
		FinalScore:        finalScore,
		ScoreBand:         scoreBand,
		PdProbability:     pdProbability,
	}

	evt, err := commonevent.NewDomainEvent(
		commonevent.LoanCreditAssessed,
		"ai-scoring-service",
		tenantID,
		"", // correlationID
		payload,
	)
	if err != nil {
		p.logger.Error("Failed to create LOAN_CREDIT_ASSESSED event",
			zap.String("loanApplicationId", loanApplicationID),
			zap.Error(err),
		)
		return
	}

	if err := p.pub.Publish(ctx, evt); err != nil {
		p.logger.Error("Failed to publish LOAN_CREDIT_ASSESSED event",
			zap.String("loanApplicationId", loanApplicationID),
			zap.Error(err),
		)
		return
	}

	p.logger.Info("Published LOAN_CREDIT_ASSESSED",
		zap.String("loanApplicationId", loanApplicationID),
		zap.Int64("customerId", customerID),
	)
}
