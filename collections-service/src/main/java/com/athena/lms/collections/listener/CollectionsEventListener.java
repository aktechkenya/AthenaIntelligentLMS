package com.athena.lms.collections.listener;

import com.athena.lms.collections.service.CollectionsService;
import com.athena.lms.common.event.EventTypes;
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
public class CollectionsEventListener {

    private final CollectionsService collectionsService;

    @RabbitListener(queues = "#{@collectionsQueue}")
    public void onLmsEvent(Map<String, Object> payload) {
        try {
            String eventType = resolveEventType(payload);
            log.debug("Received LMS event: {}", eventType);

            if (EventTypes.LOAN_DPD_UPDATED.equals(eventType)) {
                handleDpdUpdated(payload);
            } else if (EventTypes.LOAN_STAGE_CHANGED.equals(eventType)) {
                handleStageChanged(payload);
            } else if (EventTypes.LOAN_CLOSED.equals(eventType) || EventTypes.LOAN_WRITTEN_OFF.equals(eventType)) {
                handleLoanClosed(payload, EventTypes.LOAN_WRITTEN_OFF.equals(eventType));
            } else {
                log.debug("Unhandled event type in collections: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing collections event: {}", e.getMessage(), e);
        }
    }

    private void handleDpdUpdated(Map<String, Object> payload) {
        Object data = payload.get("payload");
        Map<String, Object> eventPayload = extractPayload(payload);

        String loanIdStr = getString(eventPayload, "loanId");
        String tenantId = getString(payload, "tenantId");
        if (loanIdStr == null || tenantId == null) return;

        UUID loanId = UUID.fromString(loanIdStr);
        int dpd = getInt(eventPayload, "dpd");
        BigDecimal outstanding = getBigDecimal(eventPayload, "outstandingAmount");

        collectionsService.updateDpd(loanId, dpd, outstanding, tenantId);
    }

    private void handleStageChanged(Map<String, Object> payload) {
        Map<String, Object> eventPayload = extractPayload(payload);

        String loanIdStr = getString(eventPayload, "loanId");
        String tenantId = getString(payload, "tenantId");
        String newStage = getString(eventPayload, "newStage");
        if (loanIdStr == null || tenantId == null || newStage == null) return;

        collectionsService.handleStageChange(UUID.fromString(loanIdStr), newStage, tenantId);
    }

    private void handleLoanClosed(Map<String, Object> payload, boolean writtenOff) {
        Map<String, Object> eventPayload = extractPayload(payload);

        String loanIdStr = getString(eventPayload, "loanId");
        String tenantId = getString(payload, "tenantId");
        if (loanIdStr == null || tenantId == null) return;

        try {
            com.athena.lms.collections.dto.response.CollectionCaseResponse caseResp =
                    collectionsService.getCaseByLoan(UUID.fromString(loanIdStr), tenantId);
            collectionsService.closeCase(caseResp.getId(), tenantId);
            log.info("Closed collection case for loan {} (writtenOff={})", loanIdStr, writtenOff);
        } catch (com.athena.lms.common.exception.ResourceNotFoundException e) {
            log.debug("No collection case to close for loan {}", loanIdStr);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        Object p = envelope.get("payload");
        if (p instanceof Map) return (Map<String, Object>) p;
        return envelope;
    }

    private String resolveEventType(Map<String, Object> payload) {
        Object type = payload.get("type");
        if (type != null) return type.toString();
        Object eventType = payload.get("eventType");
        if (eventType != null) return eventType.toString();
        return "";
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
