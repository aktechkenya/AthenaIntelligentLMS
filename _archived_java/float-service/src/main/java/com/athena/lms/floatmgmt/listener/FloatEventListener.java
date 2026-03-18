package com.athena.lms.floatmgmt.listener;

import com.athena.lms.floatmgmt.config.RabbitMQConfig;
import com.athena.lms.floatmgmt.service.FloatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FloatEventListener {

    private final FloatService floatService;

    /**
     * Handles account.credit.received events — tops up the float.
     * Queue bound via LmsRabbitMQConfig floatQueue: "athena.lms.float.queue".
     */
    @RabbitListener(queues = "#{@floatQueue}")
    public void onAccountCredit(Map<String, Object> payload) {
        try {
            String tenantId = extractString(payload, "tenantId");
            BigDecimal amount = extractBigDecimal(payload, "amount");
            String accountId = extractString(payload, "accountId");
            log.info("Received account.credit.received event for tenant {} amount {}", tenantId, amount);
            floatService.processTopUp(accountId, amount, tenantId);
        } catch (Exception e) {
            log.error("Failed to process account.credit.received event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles loan.disbursed events — draws float and creates an allocation.
     */
    @RabbitListener(queues = RabbitMQConfig.FLOAT_INBOUND_QUEUE)
    public void onLoanDisbursed(Map<String, Object> payload) {
        try {
            String tenantId = extractString(payload, "tenantId");
            BigDecimal amount = extractBigDecimal(payload, "amount");
            // Use applicationId as the reference since loanId isn't available at disbursement time
            String refId = extractString(payload, "applicationId");
            if (refId == null || refId.isEmpty()) {
                refId = extractString(payload, "loanId");
            }
            if (refId == null || refId.isEmpty()) {
                log.warn("No applicationId or loanId found in loan.disbursed event, skipping float draw");
                return;
            }
            // Parse as UUID; if applicationId is not a UUID, use a deterministic UUID from its hashCode
            UUID loanRef;
            try {
                loanRef = UUID.fromString(refId);
            } catch (IllegalArgumentException e) {
                loanRef = UUID.nameUUIDFromBytes(refId.getBytes());
            }
            log.info("Received loan.disbursed event for ref {} tenant {} amount {}", loanRef, tenantId, amount);
            floatService.processDraw(loanRef, amount, tenantId);
        } catch (Exception e) {
            log.error("Failed to process loan.disbursed event: {}", e.getMessage(), e);
        }
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        return val != null ? val.toString() : "";
    }

    private BigDecimal extractBigDecimal(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        return new BigDecimal(val.toString());
    }
}
