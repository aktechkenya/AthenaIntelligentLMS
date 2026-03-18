package com.athena.lms.compliance.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.common.event.EventTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishAmlAlertRaised(UUID alertId, String alertType, String customerId, String tenantId) {
        try {
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    EventTypes.AML_ALERT_RAISED,
                    "compliance-service",
                    tenantId,
                    Map.of(
                            "alertId", alertId.toString(),
                            "alertType", alertType,
                            "customerId", customerId != null ? customerId : ""
                    )
            );
            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, EventTypes.AML_ALERT_RAISED, event);
            log.info("Published AML alert raised event for alertId={}", alertId);
        } catch (Exception e) {
            log.error("Failed to publish AML alert raised event for alertId={}: {}", alertId, e.getMessage(), e);
        }
    }

    public void publishSarFiled(UUID alertId, String sarRef, String tenantId) {
        try {
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    EventTypes.AML_SAR_FILED,
                    "compliance-service",
                    tenantId,
                    Map.of(
                            "alertId", alertId.toString(),
                            "sarReference", sarRef
                    )
            );
            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, EventTypes.AML_SAR_FILED, event);
            log.info("Published SAR filed event for alertId={}, ref={}", alertId, sarRef);
        } catch (Exception e) {
            log.error("Failed to publish SAR filed event for alertId={}: {}", alertId, e.getMessage(), e);
        }
    }

    public void publishKycPassed(String customerId, String tenantId) {
        try {
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    EventTypes.CUSTOMER_KYC_PASSED,
                    "compliance-service",
                    tenantId,
                    Map.of("customerId", customerId)
            );
            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, EventTypes.CUSTOMER_KYC_PASSED, event);
            log.info("Published KYC passed event for customerId={}", customerId);
        } catch (Exception e) {
            log.error("Failed to publish KYC passed event for customerId={}: {}", customerId, e.getMessage(), e);
        }
    }

    public void publishKycFailed(String customerId, String failureReason, String tenantId) {
        try {
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    EventTypes.CUSTOMER_KYC_FAILED,
                    "compliance-service",
                    tenantId,
                    Map.of(
                            "customerId", customerId,
                            "failureReason", failureReason != null ? failureReason : ""
                    )
            );
            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, EventTypes.CUSTOMER_KYC_FAILED, event);
            log.info("Published KYC failed event for customerId={}", customerId);
        } catch (Exception e) {
            log.error("Failed to publish KYC failed event for customerId={}: {}", customerId, e.getMessage(), e);
        }
    }
}
