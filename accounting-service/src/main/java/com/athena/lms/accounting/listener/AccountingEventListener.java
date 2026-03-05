package com.athena.lms.accounting.listener;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.accounting.service.AccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumes events from athena.lms.accounting.queue (bound to loan.#, payment.#, float.#, account.#).
 * Handles both raw-map events (from loan services) and DomainEvent-wrapped events (from payment/account).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingEventListener {

    private final AccountingService accountingService;

    @RabbitListener(queues = LmsRabbitMQConfig.ACCOUNTING_QUEUE)
    public void onEvent(Map<String, Object> message) {
        try {
            // Determine event type — handle both DomainEvent envelope and raw map
            String eventType = resolveEventType(message);
            if (eventType == null) {
                log.debug("Could not resolve event type, skipping");
                return;
            }

            String tenantId = resolveTenantId(message);
            Map<String, Object> payload = resolvePayload(message);

            log.info("Accounting processing event [{}] for tenant [{}]", eventType, tenantId);

            switch (eventType) {
                case "loan.disbursed"            -> handleLoanDisbursed(payload, tenantId);
                case "payment.completed"         -> handlePaymentCompleted(payload, tenantId);
                case "payment.reversed"          -> handlePaymentReversed(payload, tenantId);
                case "loan.closed"               -> handleLoanClosed(payload, tenantId);
                case "loan.stage.changed"        -> handleStageChanged(payload, tenantId);
                case "overdraft.drawn"           -> handleOverdraftDrawn(payload, tenantId);
                case "overdraft.repaid"          -> handleOverdraftRepaid(payload, tenantId);
                case "overdraft.interest.charged" -> handleOverdraftInterestCharged(payload, tenantId);
                case "overdraft.fee.charged"     -> handleOverdraftFeeCharged(payload, tenantId);
                default -> log.debug("No accounting handler for event: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process accounting event: {}", e.getMessage(), e);
        }
    }

    private void handleLoanDisbursed(Map<String, Object> payload, String tenantId) {
        String sourceId = getStr(payload, "applicationId");
        if (accountingService.entryExists("loan.disbursed", sourceId)) return;

        BigDecimal amount = getBigDecimal(payload, "amount");
        accountingService.postLoanDisbursement(tenantId, sourceId, amount);
    }

    private void handlePaymentCompleted(Map<String, Object> payload, String tenantId) {
        String sourceId = getStr(payload, "paymentId");
        if (sourceId == null) sourceId = getStr(payload, "internalReference");
        if (accountingService.entryExists("payment.completed", sourceId)) return;

        BigDecimal amount = getBigDecimal(payload, "amount");
        String paymentType = getStr(payload, "paymentType");

        // Skip disbursements — already handled by loan.disbursed
        if ("LOAN_DISBURSEMENT".equals(paymentType)) return;

        accountingService.postRepayment(tenantId, sourceId, amount, payload);
    }

    private void handlePaymentReversed(Map<String, Object> payload, String tenantId) {
        String sourceId = getStr(payload, "paymentId");
        if (sourceId == null) return;
        BigDecimal amount = getBigDecimal(payload, "amount");
        accountingService.postPaymentReversal(tenantId, sourceId, amount);
    }

    private void handleLoanClosed(Map<String, Object> payload, String tenantId) {
        String loanId = getStr(payload, "loanId");
        log.info("Loan closed [{}] — no accounting entry required at close (balance already zeroed by repayments)", loanId);
    }

    private void handleStageChanged(Map<String, Object> payload, String tenantId) {
        String loanId   = getStr(payload, "loanId");
        String newStage = getStr(payload, "newStage");
        log.info("Loan [{}] stage changed to [{}] — provision review may be required", loanId, newStage);
        // Provision entries would be posted here in a full IFRS 9 implementation
    }

    // ─── Overdraft handlers ──────────────────────────────────────────────────────

    private void handleOverdraftDrawn(Map<String, Object> payload, String tenantId) {
        String walletId = getStr(payload, "walletId");
        String sourceId = "OD-DRAW-" + walletId;
        if (accountingService.entryExists("overdraft.drawn", sourceId)) return;
        BigDecimal amount = getBigDecimal(payload, "amount");
        // DR 1250 Overdraft Receivable / CR 1000 Cash
        accountingService.postOverdraftDrawn(tenantId, sourceId, amount);
    }

    private void handleOverdraftRepaid(Map<String, Object> payload, String tenantId) {
        String walletId = getStr(payload, "walletId");
        String sourceId = "OD-RPMT-" + walletId + "-" + System.currentTimeMillis();
        if (accountingService.entryExists("overdraft.repaid", sourceId)) return;
        BigDecimal amount = getBigDecimal(payload, "amount");
        // DR 1000 Cash / CR 1250 Overdraft Receivable
        accountingService.postOverdraftRepaid(tenantId, sourceId, amount);
    }

    private void handleOverdraftInterestCharged(Map<String, Object> payload, String tenantId) {
        String walletId = getStr(payload, "walletId");
        String sourceId = "OD-INT-" + walletId + "-" + System.currentTimeMillis();
        if (accountingService.entryExists("overdraft.interest.charged", sourceId)) return;
        BigDecimal interest = getBigDecimal(payload, "interestCharged");
        // DR 1250 Overdraft Receivable / CR 4300 Overdraft Interest Income
        accountingService.postOverdraftInterestCharged(tenantId, sourceId, interest);
    }

    private void handleOverdraftFeeCharged(Map<String, Object> payload, String tenantId) {
        String walletId = getStr(payload, "walletId");
        String reference = getStr(payload, "reference");
        String sourceId = "OD-FEE-" + (reference != null ? reference : walletId);
        if (accountingService.entryExists("overdraft.fee.charged", sourceId)) return;
        BigDecimal amount = getBigDecimal(payload, "amount");
        // DR 1250 Overdraft Receivable / CR 4100 Fee Income
        accountingService.postOverdraftFeeCharged(tenantId, sourceId, amount);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String resolveEventType(Map<String, Object> message) {
        // Raw map (loan services): has "eventType" key at top level
        if (message.containsKey("eventType")) return getStr(message, "eventType");
        // DomainEvent envelope: has "type" key at top level
        if (message.containsKey("type")) return getStr(message, "type");
        return null;
    }

    @SuppressWarnings("unchecked")
    private String resolveTenantId(Map<String, Object> message) {
        if (message.containsKey("payload")) {
            Object p = message.get("payload");
            if (p instanceof Map) return getStr((Map<String, Object>) p, "tenantId");
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

    private BigDecimal getBigDecimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return BigDecimal.ZERO;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
