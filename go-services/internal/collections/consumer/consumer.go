package consumer

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/collections/service"
	commonevent "github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/errors"
)

// CollectionsEventListener handles inbound domain events for the collections service.
type CollectionsEventListener struct {
	svc    *service.CollectionsService
	logger *zap.Logger
}

// NewCollectionsEventListener creates a new event listener.
func NewCollectionsEventListener(svc *service.CollectionsService, logger *zap.Logger) *CollectionsEventListener {
	return &CollectionsEventListener{svc: svc, logger: logger}
}

// Handle processes a single domain event. Implements event.Handler signature.
func (l *CollectionsEventListener) Handle(ctx context.Context, evt *commonevent.DomainEvent) error {
	l.logger.Debug("Received LMS event", zap.String("type", evt.Type))

	switch evt.Type {
	case commonevent.LoanDPDUpdated:
		return l.handleDPDUpdated(ctx, evt)
	case commonevent.LoanStageChanged:
		return l.handleStageChanged(ctx, evt)
	case commonevent.LoanClosed:
		return l.handleLoanClosed(ctx, evt, false)
	case commonevent.LoanWrittenOff:
		return l.handleLoanClosed(ctx, evt, true)
	case commonevent.LoanRepaymentReceived:
		return l.handleRepaymentReceived(ctx, evt)
	default:
		l.logger.Debug("Unhandled event type in collections", zap.String("type", evt.Type))
		return nil
	}
}

type dpdPayload struct {
	LoanID            string          `json:"loanId"`
	DPD               json.Number     `json:"dpd"`
	OutstandingAmount json.RawMessage `json:"outstandingAmount"`
}

func (l *CollectionsEventListener) handleDPDUpdated(ctx context.Context, evt *commonevent.DomainEvent) error {
	var p dpdPayload
	if err := evt.UnmarshalPayload(&p); err != nil {
		return fmt.Errorf("unmarshal dpd payload: %w", err)
	}
	if p.LoanID == "" || evt.TenantID == "" {
		return nil
	}

	loanID, err := uuid.Parse(p.LoanID)
	if err != nil {
		return fmt.Errorf("parse loanId: %w", err)
	}

	dpd, err := p.DPD.Int64()
	if err != nil {
		dpd = 0
	}

	var outstanding *decimal.Decimal
	if len(p.OutstandingAmount) > 0 && string(p.OutstandingAmount) != "null" {
		var d decimal.Decimal
		if err := json.Unmarshal(p.OutstandingAmount, &d); err == nil {
			outstanding = &d
		}
	}

	return l.svc.UpdateDPD(ctx, loanID, int(dpd), outstanding, evt.TenantID)
}

type stagePayload struct {
	LoanID   string `json:"loanId"`
	NewStage string `json:"newStage"`
}

func (l *CollectionsEventListener) handleStageChanged(ctx context.Context, evt *commonevent.DomainEvent) error {
	var p stagePayload
	if err := evt.UnmarshalPayload(&p); err != nil {
		return fmt.Errorf("unmarshal stage payload: %w", err)
	}
	if p.LoanID == "" || evt.TenantID == "" || p.NewStage == "" {
		return nil
	}

	loanID, err := uuid.Parse(p.LoanID)
	if err != nil {
		return fmt.Errorf("parse loanId: %w", err)
	}

	return l.svc.HandleStageChange(ctx, loanID, p.NewStage, evt.TenantID)
}

type loanClosedPayload struct {
	LoanID string `json:"loanId"`
}

func (l *CollectionsEventListener) handleLoanClosed(ctx context.Context, evt *commonevent.DomainEvent, writtenOff bool) error {
	var p loanClosedPayload
	if err := evt.UnmarshalPayload(&p); err != nil {
		return fmt.Errorf("unmarshal loan closed payload: %w", err)
	}
	if p.LoanID == "" || evt.TenantID == "" {
		return nil
	}

	loanID, err := uuid.Parse(p.LoanID)
	if err != nil {
		return fmt.Errorf("parse loanId: %w", err)
	}

	caseResp, err := l.svc.GetCaseByLoan(ctx, loanID, evt.TenantID)
	if err != nil {
		// If no case exists, that's fine
		if _, ok := err.(*errors.NotFoundError); ok {
			l.logger.Debug("No collection case to close for loan", zap.String("loanId", p.LoanID))
			return nil
		}
		return err
	}

	_, err = l.svc.CloseCase(ctx, caseResp.ID, evt.TenantID)
	if err != nil {
		return err
	}
	l.logger.Info("Closed collection case for loan",
		zap.String("loanId", p.LoanID),
		zap.Bool("writtenOff", writtenOff),
	)
	return nil
}

type repaymentPayload struct {
	LoanID string          `json:"loanId"`
	Amount json.RawMessage `json:"amount"`
}

func (l *CollectionsEventListener) handleRepaymentReceived(ctx context.Context, evt *commonevent.DomainEvent) error {
	var p repaymentPayload
	if err := evt.UnmarshalPayload(&p); err != nil {
		return fmt.Errorf("unmarshal repayment payload: %w", err)
	}
	if p.LoanID == "" || evt.TenantID == "" {
		return nil
	}

	loanID, err := uuid.Parse(p.LoanID)
	if err != nil {
		return fmt.Errorf("parse loanId: %w", err)
	}

	var amount decimal.Decimal
	if len(p.Amount) > 0 && string(p.Amount) != "null" {
		if err := json.Unmarshal(p.Amount, &amount); err != nil {
			l.logger.Warn("Failed to parse repayment amount, skipping PTP fulfilment",
				zap.String("loanId", p.LoanID),
				zap.Error(err),
			)
			return nil
		}
	}

	if err := l.svc.FulfillPtpsForPayment(ctx, loanID, amount, evt.TenantID); err != nil {
		return fmt.Errorf("fulfil ptps for payment: %w", err)
	}
	return nil
}
