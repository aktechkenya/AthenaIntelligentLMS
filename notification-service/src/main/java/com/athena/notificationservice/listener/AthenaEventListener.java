package com.athena.notificationservice.listener;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens on athena.lms.notification.queue (bound "#" — all LMS events).
 * Handles key lifecycle events and logs them; actual email delivery requires
 * a configured EMAIL notification config in the DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AthenaEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = LmsRabbitMQConfig.NOTIFICATION_QUEUE)
    public void onLmsEvent(Map<String, Object> message) {
        try {
            String eventType = resolveEventType(message);
            if (eventType == null) {
                log.debug("[NOTIFICATION] Could not resolve event type, skipping");
                return;
            }

            String tenantId = resolveTenantId(message);
            Map<String, Object> payload = resolvePayload(message);

            log.info("[NOTIFICATION] event={} tenant={}", eventType, tenantId);

            switch (eventType) {
                case "loan.application.submitted" -> handleLoanSubmitted(payload);
                case "loan.disbursed"              -> handleLoanDisbursed(payload);
                case "payment.completed"           -> handlePaymentCompleted(payload);
                case "customer.kyc.verified"       -> handleKycVerified(payload);
                case "loan.stage.changed"          -> handleStageChanged(payload);
                // Legacy AthenaCreditScore events
                case "DISPUTE_FILED"  -> handleDisputeFiled(payload);
                case "SCORE_UPDATED"  -> handleScoreUpdated(payload);
                case "CONSENT_GRANTED" -> handleConsentGranted(payload);
                case "USER_INVITATION" -> handleUserInvitation(payload);
                default -> log.debug("[NOTIFICATION] No handler for event: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[NOTIFICATION] Failed to process event: {}", e.getMessage(), e);
        }
    }

    // ─── LMS event handlers ──────────────────────────────────────────────────

    private void handleLoanSubmitted(Map<String, Object> payload) {
        String customerId = getStr(payload, "customerId");
        String applicationId = getStr(payload, "applicationId");
        log.info("[NOTIFICATION] Loan application {} submitted for customer {}", applicationId, customerId);
        notificationService.sendEmail(
            "loan-origination-service",
            resolveRecipient(customerId),
            "Loan Application Received — Athena LMS",
            String.format(
                "Dear Customer,\n\nYour loan application (Ref: %s) has been received " +
                "and is currently under review.\n\nWe will update you on the progress " +
                "within 2 working days.\n\nRegards,\nAthena LMS Team",
                applicationId));
    }

    private void handleLoanDisbursed(Map<String, Object> payload) {
        String customerId = getStr(payload, "customerId");
        Object amount = payload.getOrDefault("amount", "N/A");
        String account = getStr(payload, "disbursementAccount");
        log.info("[NOTIFICATION] Loan disbursed to customer {} amount={}", customerId, amount);
        notificationService.sendEmail(
            "loan-management-service",
            resolveRecipient(customerId),
            "Loan Disbursed — Athena LMS",
            String.format(
                "Dear Customer,\n\nYour loan of KES %s has been disbursed to account %s.\n\n" +
                "Your repayment schedule is now active. Please ensure timely repayments " +
                "to maintain a good credit standing.\n\nRegards,\nAthena LMS Team",
                amount, account != null ? account : "your registered account"));
    }

    private void handlePaymentCompleted(Map<String, Object> payload) {
        String customerId = getStr(payload, "customerId");
        Object amount = payload.getOrDefault("amount", "N/A");
        Object outstanding = payload.getOrDefault("outstandingBalance", "N/A");
        log.info("[NOTIFICATION] Repayment received for customer {} amount={}", customerId, amount);
        notificationService.sendEmail(
            "payment-service",
            resolveRecipient(customerId),
            "Repayment Confirmed — Athena LMS",
            String.format(
                "Dear Customer,\n\nYour repayment of KES %s has been received and processed.\n\n" +
                "Outstanding balance: KES %s\n\nThank you for your payment.\n\nRegards,\nAthena LMS Team",
                amount, outstanding));
    }

    private void handleKycVerified(Map<String, Object> payload) {
        String customerId = getStr(payload, "customerId");
        log.info("[NOTIFICATION] KYC verified for customer {}", customerId);
        notificationService.sendEmail(
            "compliance-service",
            resolveRecipient(customerId),
            "KYC Verification Approved — Athena LMS",
            "Dear Customer,\n\nYour identity verification (KYC) has been successfully approved. " +
            "You are now eligible to apply for loan products on the Athena LMS platform.\n\n" +
            "Regards,\nAthena LMS Team");
    }

    private void handleStageChanged(Map<String, Object> payload) {
        String newStage = getStr(payload, "newStage");
        if ("OVERDUE".equals(newStage) || "DEFAULTED".equals(newStage)) {
            String loanId = getStr(payload, "loanId");
            String customerId = getStr(payload, "customerId");
            log.warn("[NOTIFICATION] Loan {} moved to {} — alerting collections", loanId, newStage);
            notificationService.sendEmail(
                "loan-management-service",
                "collections@athena.lms",
                String.format("ALERT: Loan %s moved to %s", loanId, newStage),
                String.format("Loan %s for customer %s has moved to stage: %s.\n\nPlease initiate collections process.",
                    loanId, customerId, newStage));
        }
    }

    // ─── Legacy AthenaCreditScore event handlers ─────────────────────────────

    private void handleDisputeFiled(Map<String, Object> payload) {
        String email = getStr(payload, "email");
        String disputeId = getStr(payload, "disputeId");
        Object customerId = payload.get("customerId");
        log.info("[NOTIFICATION] DISPUTE_FILED disputeId={} customerId={}", disputeId, customerId);
        if (email != null) {
            notificationService.sendDisputeAcknowledgement(email, disputeId, null);
        }
    }

    private void handleScoreUpdated(Map<String, Object> payload) {
        String email = getStr(payload, "email");
        Object score = payload.get("score");
        log.info("[NOTIFICATION] SCORE_UPDATED score={}", score);
        if (email != null) {
            notificationService.sendScoreUpdateNotification(email, score, null);
        }
    }

    private void handleConsentGranted(Map<String, Object> payload) {
        String email = getStr(payload, "email");
        Object partnerId = payload.get("partnerId");
        log.info("[NOTIFICATION] CONSENT_GRANTED partnerId={}", partnerId);
        if (email != null) {
            notificationService.sendConsentGrantedNotification(email, partnerId, null);
        }
    }

    private void handleUserInvitation(Map<String, Object> payload) {
        String email = getStr(payload, "email");
        String token = getStr(payload, "token");
        log.info("[NOTIFICATION] USER_INVITATION email={}", email);
        if (email != null && token != null) {
            notificationService.sendEmail("user-service", email,
                "You've been invited to Athena",
                String.format("Hello,\n\nComplete your registration:\nhttp://localhost:5173/complete-registration?token=%s\n\nThis link expires in 24 hours.\n\nRegards,\nAthena Team", token));
        }
    }

    // ─── Legacy athena.dispute.queue (backward compat) ───────────────────────

    @RabbitListener(queues = "athena.dispute.queue")
    public void handleDisputeEvent(Map<String, Object> event) {
        String type = getStr(event, "type");
        log.info("[DISPUTE-EVENT] type={}", type);
        // Routes to compliance team mailbox — configure via /api/v1/notifications/config
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String resolveEventType(Map<String, Object> message) {
        if (message.containsKey("eventType")) return getStr(message, "eventType");
        if (message.containsKey("type"))      return getStr(message, "type");
        return null;
    }

    private String resolveTenantId(Map<String, Object> message) {
        if (message.containsKey("payload")) {
            Object p = message.get("payload");
            if (p instanceof Map<?, ?> pm) return getStr((Map<String, Object>) pm, "tenantId");
        }
        return getStr(message, "tenantId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolvePayload(Map<String, Object> message) {
        if (message.containsKey("payload")) {
            Object p = message.get("payload");
            if (p instanceof Map) return (Map<String, Object>) p;
        }
        return message;
    }

    private String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Resolve recipient email from customerId.
     * In production this would call customer-service; here we log and use a placeholder.
     * Actual delivery only happens if email config is enabled in notification_configs table.
     */
    private String resolveRecipient(String customerId) {
        log.debug("[NOTIFICATION] Resolving recipient for customerId={} (using placeholder)", customerId);
        return "noreply@athena.lms"; // placeholder — real lookup would call customer-service
    }
}
