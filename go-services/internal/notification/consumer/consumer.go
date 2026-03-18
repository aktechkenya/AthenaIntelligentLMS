package consumer

import (
	"context"
	"encoding/json"
	"fmt"

	"go.uber.org/zap"

	"github.com/athena-lms/go-services/internal/common/event"
	"github.com/athena-lms/go-services/internal/common/rabbitmq"
	"github.com/athena-lms/go-services/internal/notification/client"
	"github.com/athena-lms/go-services/internal/notification/service"
)

// Consumer listens on the notification queue (bound "#" — all LMS events)
// and dispatches to the appropriate email handler.
type Consumer struct {
	svc            *service.Service
	customerClient *client.CustomerClient
	conn           *rabbitmq.Connection
	logger         *zap.Logger
}

// New creates a new Consumer.
func New(svc *service.Service, customerClient *client.CustomerClient, conn *rabbitmq.Connection, logger *zap.Logger) *Consumer {
	return &Consumer{
		svc:            svc,
		customerClient: customerClient,
		conn:           conn,
		logger:         logger,
	}
}

// Start begins consuming messages. Blocks until ctx is cancelled.
func (c *Consumer) Start(ctx context.Context) error {
	ec := event.NewConsumer(c.conn, rabbitmq.NotificationQueue, 3, 5, c.handleEvent, c.logger)
	return ec.Start(ctx)
}

// handleEvent processes a single domain event.
func (c *Consumer) handleEvent(ctx context.Context, evt *event.DomainEvent) error {
	c.logger.Info("Notification event received",
		zap.String("event", evt.Type),
		zap.String("tenant", evt.TenantID),
	)

	// Parse payload into a generic map
	payload := make(map[string]any)
	if err := json.Unmarshal(evt.Payload, &payload); err != nil {
		c.logger.Warn("Could not unmarshal event payload", zap.Error(err))
		payload = make(map[string]any)
	}

	switch evt.Type {
	case event.LoanApplicationSubmitted:
		return c.handleLoanSubmitted(ctx, payload, evt.TenantID)
	case event.LoanDisbursed:
		return c.handleLoanDisbursed(ctx, payload, evt.TenantID)
	case event.PaymentCompleted:
		return c.handlePaymentCompleted(ctx, payload, evt.TenantID)
	case event.CustomerKYCPassed:
		return c.handleKycVerified(ctx, payload, evt.TenantID)
	case event.LoanStageChanged:
		return c.handleStageChanged(ctx, payload, evt.TenantID)

	// Legacy AthenaCreditScore events
	case "DISPUTE_FILED":
		return c.handleDisputeFiled(ctx, payload)
	case "SCORE_UPDATED":
		return c.handleScoreUpdated(ctx, payload)
	case "CONSENT_GRANTED":
		return c.handleConsentGranted(ctx, payload)
	case "USER_INVITATION":
		return c.handleUserInvitation(ctx, payload)

	default:
		c.logger.Debug("No handler for event", zap.String("type", evt.Type))
		return nil
	}
}

// --- LMS event handlers ---

func (c *Consumer) handleLoanSubmitted(ctx context.Context, payload map[string]any, tenantID string) error {
	customerID := getStr(payload, "customerId")
	applicationID := getStr(payload, "applicationId")
	c.logger.Info("Loan application submitted",
		zap.String("applicationId", applicationID),
		zap.String("customerId", customerID),
	)
	return c.svc.SendEmail(ctx,
		"loan-origination-service",
		c.customerClient.ResolveEmail(customerID, tenantID),
		"Loan Application Received — Athena LMS",
		fmt.Sprintf(
			"Dear Customer,\n\nYour loan application (Ref: %s) has been received "+
				"and is currently under review.\n\nWe will update you on the progress "+
				"within 2 working days.\n\nRegards,\nAthena LMS Team",
			applicationID),
	)
}

func (c *Consumer) handleLoanDisbursed(ctx context.Context, payload map[string]any, tenantID string) error {
	customerID := getStr(payload, "customerId")
	amount := getAny(payload, "amount", "N/A")
	account := getStr(payload, "disbursementAccount")
	if account == "" {
		account = "your registered account"
	}
	c.logger.Info("Loan disbursed",
		zap.String("customerId", customerID),
		zap.Any("amount", amount),
	)
	return c.svc.SendEmail(ctx,
		"loan-management-service",
		c.customerClient.ResolveEmail(customerID, tenantID),
		"Loan Disbursed — Athena LMS",
		fmt.Sprintf(
			"Dear Customer,\n\nYour loan of KES %v has been disbursed to account %s.\n\n"+
				"Your repayment schedule is now active. Please ensure timely repayments "+
				"to maintain a good credit standing.\n\nRegards,\nAthena LMS Team",
			amount, account),
	)
}

func (c *Consumer) handlePaymentCompleted(ctx context.Context, payload map[string]any, tenantID string) error {
	customerID := getStr(payload, "customerId")
	amount := getAny(payload, "amount", "N/A")
	outstanding := getAny(payload, "outstandingBalance", "N/A")
	c.logger.Info("Payment received",
		zap.String("customerId", customerID),
		zap.Any("amount", amount),
	)
	return c.svc.SendEmail(ctx,
		"payment-service",
		c.customerClient.ResolveEmail(customerID, tenantID),
		"Repayment Confirmed — Athena LMS",
		fmt.Sprintf(
			"Dear Customer,\n\nYour repayment of KES %v has been received and processed.\n\n"+
				"Outstanding balance: KES %v\n\nThank you for your payment.\n\nRegards,\nAthena LMS Team",
			amount, outstanding),
	)
}

func (c *Consumer) handleKycVerified(ctx context.Context, payload map[string]any, tenantID string) error {
	customerID := getStr(payload, "customerId")
	c.logger.Info("KYC verified", zap.String("customerId", customerID))
	return c.svc.SendEmail(ctx,
		"compliance-service",
		c.customerClient.ResolveEmail(customerID, tenantID),
		"KYC Verification Approved — Athena LMS",
		"Dear Customer,\n\nYour identity verification (KYC) has been successfully approved. "+
			"You are now eligible to apply for loan products on the Athena LMS platform.\n\n"+
			"Regards,\nAthena LMS Team",
	)
}

func (c *Consumer) handleStageChanged(ctx context.Context, payload map[string]any, tenantID string) error {
	newStage := getStr(payload, "newStage")
	loanID := getStr(payload, "loanId")
	customerID := getStr(payload, "customerId")

	// Alert collections team for any non-PERFORMING stage
	if newStage != "" && newStage != "PERFORMING" {
		c.logger.Warn("Loan stage changed — alerting collections",
			zap.String("loanId", loanID),
			zap.String("newStage", newStage),
		)
		if err := c.svc.SendEmail(ctx,
			"loan-management-service",
			"collections@athena.lms",
			fmt.Sprintf("ALERT: Loan %s moved to %s", loanID, newStage),
			fmt.Sprintf("Loan %s for customer %s has moved to stage: %s.\n\nPlease review and initiate appropriate collections action.",
				loanID, customerID, newStage),
		); err != nil {
			return err
		}
	}

	// Notify customer for serious stages (DOUBTFUL, LOSS)
	if newStage == "DOUBTFUL" || newStage == "LOSS" {
		return c.svc.SendEmail(ctx,
			"loan-management-service",
			c.customerClient.ResolveEmail(customerID, tenantID),
			"Important: Loan Account Status Update — Athena LMS",
			fmt.Sprintf(
				"Dear Customer,\n\nYour loan (Ref: %s) has been classified as %s due to outstanding payments.\n\n"+
					"Please contact us urgently to discuss repayment options and avoid further escalation.\n\n"+
					"Regards,\nAthena LMS Team",
				loanID, newStage),
		)
	}

	return nil
}

// --- Legacy AthenaCreditScore event handlers ---

func (c *Consumer) handleDisputeFiled(ctx context.Context, payload map[string]any) error {
	email := getStr(payload, "email")
	disputeID := getStr(payload, "disputeId")
	c.logger.Info("DISPUTE_FILED", zap.String("disputeId", disputeID))
	if email != "" {
		return c.svc.SendDisputeAcknowledgement(ctx, email, disputeID)
	}
	return nil
}

func (c *Consumer) handleScoreUpdated(ctx context.Context, payload map[string]any) error {
	email := getStr(payload, "email")
	score := payload["score"]
	c.logger.Info("SCORE_UPDATED", zap.Any("score", score))
	if email != "" {
		return c.svc.SendScoreUpdateNotification(ctx, email, score)
	}
	return nil
}

func (c *Consumer) handleConsentGranted(ctx context.Context, payload map[string]any) error {
	email := getStr(payload, "email")
	partnerID := payload["partnerId"]
	c.logger.Info("CONSENT_GRANTED", zap.Any("partnerId", partnerID))
	if email != "" {
		return c.svc.SendConsentGrantedNotification(ctx, email, partnerID)
	}
	return nil
}

func (c *Consumer) handleUserInvitation(ctx context.Context, payload map[string]any) error {
	email := getStr(payload, "email")
	token := getStr(payload, "token")
	c.logger.Info("USER_INVITATION", zap.String("email", email))
	if email != "" && token != "" {
		return c.svc.SendEmail(ctx, "user-service", email,
			"You've been invited to Athena",
			fmt.Sprintf("Hello,\n\nComplete your registration:\nhttp://localhost:5173/complete-registration?token=%s\n\nThis link expires in 24 hours.\n\nRegards,\nAthena Team", token),
		)
	}
	return nil
}

// --- Helpers ---

func getStr(m map[string]any, key string) string {
	v, ok := m[key]
	if !ok || v == nil {
		return ""
	}
	return fmt.Sprintf("%v", v)
}

func getAny(m map[string]any, key string, fallback any) any {
	v, ok := m[key]
	if !ok || v == nil {
		return fallback
	}
	return v
}
