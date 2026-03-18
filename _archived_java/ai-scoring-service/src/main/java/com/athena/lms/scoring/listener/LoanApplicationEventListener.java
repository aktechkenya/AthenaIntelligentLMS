package com.athena.lms.scoring.listener;

import com.athena.lms.scoring.config.RabbitMQConfig;
import com.athena.lms.scoring.service.AiScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationEventListener {

    private final AiScoringService scoringService;

    @RabbitListener(queues = RabbitMQConfig.SCORING_INBOUND_QUEUE)
    public void handleLoanApplicationEvent(Map<String, Object> payload) {
        try {
            String eventType = resolveEventType(payload);

            UUID loanApplicationId = resolveLoanApplicationId(payload);
            if (loanApplicationId == null) {
                log.warn("Could not resolve loanApplicationId from event payload, skipping. eventType={}", eventType);
                return;
            }

            Object customerIdObj = payload.get("customerId");
            if (customerIdObj == null) {
                log.warn("Missing customerId in event payload for loanApplicationId={}, skipping.", loanApplicationId);
                return;
            }
            Long customerId;
            try {
                if (customerIdObj instanceof Number) {
                    customerId = ((Number) customerIdObj).longValue();
                } else {
                    // customerId may be a UUID string or numeric string — hash to Long for compatibility
                    String customerIdStr = customerIdObj.toString();
                    try {
                        customerId = Long.parseLong(customerIdStr);
                    } catch (NumberFormatException nfe) {
                        customerId = (long) Math.abs(customerIdStr.hashCode());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse customerId '{}', using hash. Error: {}", customerIdObj, e.getMessage());
                customerId = (long) Math.abs(customerIdObj.toString().hashCode());
            }

            String tenantId = payload.get("tenantId") != null ? payload.get("tenantId").toString() : "default";

            log.info("Received loan event: type={} loanApplicationId={} customerId={} tenantId={}",
                    eventType, loanApplicationId, customerId, tenantId);

            scoringService.triggerScoring(loanApplicationId, customerId, eventType, tenantId);

        } catch (Exception e) {
            log.error("Error processing loan application event: {}", e.getMessage(), e);
        }
    }

    private String resolveEventType(Map<String, Object> payload) {
        Object type = payload.get("type");
        if (type != null) return type.toString();
        Object eventType = payload.get("eventType");
        if (eventType != null) return eventType.toString();
        return "UNKNOWN";
    }

    private UUID resolveLoanApplicationId(Map<String, Object> payload) {
        for (String key : new String[]{"applicationId", "loanApplicationId", "id"}) {
            Object val = payload.get(key);
            if (val != null) {
                try {
                    return UUID.fromString(val.toString());
                } catch (IllegalArgumentException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }
}
