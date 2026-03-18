package com.athena.lms.fraud.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.common.event.EventTypes;
import com.athena.lms.fraud.entity.FraudAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public static final String FRAUD_ALERT_RAISED = "fraud.alert.raised";
    public static final String FRAUD_BLOCK_ACCOUNT = "fraud.block.account";

    public void publishFraudAlertRaised(FraudAlert alert) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", alert.getId().toString());
            payload.put("alertType", alert.getAlertType().name());
            payload.put("severity", alert.getSeverity().name());
            payload.put("customerId", alert.getCustomerId() != null ? alert.getCustomerId() : "");
            payload.put("ruleCode", alert.getRuleCode() != null ? alert.getRuleCode() : "");
            payload.put("description", alert.getDescription());
            if (alert.getTriggerAmount() != null) {
                payload.put("triggerAmount", alert.getTriggerAmount());
            }
            if (alert.getRiskScore() != null) {
                payload.put("riskScore", alert.getRiskScore());
            }

            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    FRAUD_ALERT_RAISED, "fraud-detection-service",
                    alert.getTenantId(), payload);

            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, FRAUD_ALERT_RAISED, event);
            log.info("Published fraud alert raised: id={} type={} severity={}",
                    alert.getId(), alert.getAlertType(), alert.getSeverity());
        } catch (Exception e) {
            log.error("Failed to publish fraud alert event for id={}: {}", alert.getId(), e.getMessage(), e);
        }
    }

    public void escalateToCompliance(FraudAlert alert) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", alert.getId().toString());
            payload.put("alertType", alert.getAlertType().name());
            payload.put("customerId", alert.getCustomerId() != null ? alert.getCustomerId() : "");
            payload.put("description", alert.getDescription());
            payload.put("severity", alert.getSeverity().name());
            if (alert.getTriggerAmount() != null) {
                payload.put("triggerAmount", alert.getTriggerAmount());
            }

            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    EventTypes.AML_ALERT_RAISED, "fraud-detection-service",
                    alert.getTenantId(), payload);

            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, EventTypes.AML_ALERT_RAISED, event);
            log.info("Escalated fraud alert to compliance: id={} type={}", alert.getId(), alert.getAlertType());
        } catch (Exception e) {
            log.error("Failed to escalate alert to compliance for id={}: {}", alert.getId(), e.getMessage(), e);
        }
    }

    public void publishSarFiled(String reportNumber, String subjectCustomerId, String tenantId) {
        try {
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    "fraud.sar.filed", "fraud-detection-service", tenantId,
                    Map.of("reportNumber", reportNumber,
                           "subjectCustomerId", subjectCustomerId != null ? subjectCustomerId : ""));
            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, "fraud.sar.filed", event);
            log.info("Published SAR filed event: report={}", reportNumber);
        } catch (Exception e) {
            log.error("Failed to publish SAR filed event: {}", e.getMessage(), e);
        }
    }

    public void publishBlockAccount(String customerId, String reason, String tenantId) {
        try {
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    FRAUD_BLOCK_ACCOUNT, "fraud-detection-service", tenantId,
                    Map.of("customerId", customerId, "reason", reason));

            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, FRAUD_BLOCK_ACCOUNT, event);
            log.info("Published block account event for customerId={}", customerId);
        } catch (Exception e) {
            log.error("Failed to publish block account event: {}", e.getMessage(), e);
        }
    }
}
