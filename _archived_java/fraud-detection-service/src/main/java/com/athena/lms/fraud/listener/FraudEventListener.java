package com.athena.lms.fraud.listener;

import com.athena.lms.fraud.config.FraudRabbitMQConfig;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudEventListener {

    private final FraudDetectionService fraudDetectionService;

    private static final Set<String> MONITORED_EVENTS = Set.of(
            // Payment & transaction events
            "payment.completed", "payment.reversed", "payment.initiated",
            "transfer.completed", "transfer.initiated",
            "account.credit.received", "account.debit.processed",
            "account.unfrozen",
            // Loan events
            "loan.application.submitted", "loan.disbursed",
            "loan.closed", "loan.written.off", "loan.repayment.received",
            "loan.dpd.updated",
            // Customer events
            "customer.created", "customer.updated",
            // Overdraft events
            "overdraft.drawn", "overdraft.applied",
            // Mobile/BNPL events
            "mobile.transfer.completed", "shop.bnpl.approved",
            "shop.order.placed",
            // Float events
            "float.drawn"
    );

    @RabbitListener(queues = FraudRabbitMQConfig.FRAUD_QUEUE, concurrency = "3-5")
    public void handleEvent(Map<String, Object> payload) {
        try {
            String eventType = resolveEventType(payload);
            if (eventType == null) {
                log.trace("Received event with no resolvable type, skipping");
                return;
            }

            // Skip events we don't monitor to avoid unnecessary processing
            if (!MONITORED_EVENTS.contains(eventType)) {
                log.trace("Skipping unmonitored event type={}", eventType);
                return;
            }

            String tenantId = extractString(payload, "tenantId");
            if (tenantId == null) {
                tenantId = "unknown";
            }

            log.debug("Fraud listener processing event type={} tenant={}", eventType, tenantId);

            List<FraudAlert> alerts = fraudDetectionService.processEvent(tenantId, eventType, payload);

            if (!alerts.isEmpty()) {
                log.info("Fraud detection triggered {} alert(s) for event type={} tenant={}",
                        alerts.size(), eventType, tenantId);
            }
        } catch (Exception e) {
            log.error("Error processing fraud event: {}", e.getMessage(), e);
        }
    }

    private String resolveEventType(Map<String, Object> payload) {
        Object type = payload.get("type");
        if (type != null) return type.toString();
        Object eventType = payload.get("eventType");
        if (eventType != null) return eventType.toString();
        return null;
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        return val != null ? val.toString() : null;
    }
}
