package com.athena.lms.compliance.listener;

import com.athena.lms.common.event.EventTypes;
import com.athena.lms.compliance.entity.KycRecord;
import com.athena.lms.compliance.enums.KycStatus;
import com.athena.lms.compliance.repository.KycRepository;
import com.athena.lms.compliance.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceEventListener {

    private final ComplianceService complianceService;
    private final KycRepository kycRepository;

    @RabbitListener(queues = "#{@complianceQueue}")
    @Transactional
    public void handleEvent(Map<String, Object> payload) {
        try {
            String eventType = resolveEventType(payload);
            if (eventType == null) {
                log.warn("Received compliance event with no resolvable event type: {}", payload);
                return;
            }

            log.info("Compliance listener received event type={}", eventType);

            String tenantId = extractString(payload, "tenantId", "unknown");
            Object innerPayload = payload.get("payload");
            String subjectId = extractSubjectId(innerPayload);
            String payloadStr = innerPayload != null ? innerPayload.toString() : null;

            // Log all events to compliance_events table
            complianceService.logEvent(eventType, extractString(payload, "source", "unknown"),
                    subjectId, payloadStr, tenantId);

            // Handle specific event types
            switch (eventType) {
                case EventTypes.AML_ALERT_RAISED -> handleAmlAlertRaised(innerPayload, tenantId);
                case EventTypes.CUSTOMER_KYC_PASSED -> handleKycPassed(innerPayload, tenantId);
                case EventTypes.CUSTOMER_KYC_FAILED -> handleKycFailed(innerPayload, tenantId);
                default -> log.debug("No specific handler for event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing compliance event: {}", e.getMessage(), e);
        }
    }

    private String resolveEventType(Map<String, Object> payload) {
        Object type = payload.get("type");
        if (type != null) return type.toString();
        Object eventType = payload.get("eventType");
        if (eventType != null) return eventType.toString();
        return null;
    }

    private void handleAmlAlertRaised(Object innerPayload, String tenantId) {
        log.info("Processing AML alert raised event for tenant={}", tenantId);
        // Alert already stored by the service that raised it; event logged above
    }

    private void handleKycPassed(Object innerPayload, String tenantId) {
        try {
            if (innerPayload instanceof Map<?, ?> map) {
                Object customerIdObj = map.get("customerId");
                if (customerIdObj != null) {
                    Long customerId = Long.valueOf(customerIdObj.toString());
                    Optional<KycRecord> record = kycRepository.findByTenantIdAndCustomerId(tenantId, customerId);
                    record.ifPresent(kyc -> {
                        kyc.setStatus(KycStatus.PASSED);
                        kyc.setCheckedAt(OffsetDateTime.now());
                        kycRepository.save(kyc);
                        log.info("KYC record updated to PASSED for customerId={} tenant={}", customerId, tenantId);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle KYC passed event: {}", e.getMessage(), e);
        }
    }

    private void handleKycFailed(Object innerPayload, String tenantId) {
        try {
            if (innerPayload instanceof Map<?, ?> map) {
                Object customerIdObj = map.get("customerId");
                if (customerIdObj != null) {
                    Long customerId = Long.valueOf(customerIdObj.toString());
                    String failureReason = extractString((Map<?, ?>) map, "failureReason", null);
                    Optional<KycRecord> record = kycRepository.findByTenantIdAndCustomerId(tenantId, customerId);
                    record.ifPresent(kyc -> {
                        kyc.setStatus(KycStatus.FAILED);
                        kyc.setFailureReason(failureReason);
                        kyc.setCheckedAt(OffsetDateTime.now());
                        kycRepository.save(kyc);
                        log.info("KYC record updated to FAILED for customerId={} tenant={}", customerId, tenantId);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle KYC failed event: {}", e.getMessage(), e);
        }
    }

    private String extractSubjectId(Object innerPayload) {
        if (innerPayload instanceof Map<?, ?> map) {
            Object alertId = map.get("alertId");
            if (alertId != null) return alertId.toString();
            Object customerId = map.get("customerId");
            if (customerId != null) return customerId.toString();
        }
        return null;
    }

    private String extractString(Map<?, ?> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
