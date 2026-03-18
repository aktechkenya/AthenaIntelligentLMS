package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.request.CreateCaseRequest;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.AlertType;
import com.athena.lms.fraud.event.FraudEventPublisher;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoActionService {

    private final FraudAlertRepository alertRepository;
    private final CaseManagementService caseManagementService;
    private final NetworkAnalysisService networkAnalysisService;
    private final FraudEventPublisher eventPublisher;

    private static final int AUTO_CASE_THRESHOLD = 3;

    public void processAutoActions(String tenantId, List<FraudAlert> alerts, Map<String, Object> eventData) {
        for (FraudAlert alert : alerts) {
            // Auto-block on CRITICAL watchlist/structuring alerts
            if (shouldAutoBlock(alert)) {
                log.info("AUTO-BLOCK triggered for customer={} alert={}", alert.getCustomerId(), alert.getId());
                caseManagementService.audit(tenantId, "AUTO_BLOCK", "ALERT", alert.getId(),
                        "system", "Account auto-blocked due to CRITICAL alert: " + alert.getAlertType(), null);
                eventPublisher.publishBlockAccount(alert.getCustomerId(),
                        "CRITICAL " + alert.getAlertType() + " alert", tenantId);
            }

            // Auto-create case when customer has 3+ open alerts
            if (alert.getCustomerId() != null) {
                long openCount = alertRepository.countOpenAlertsByCustomer(tenantId, alert.getCustomerId());
                if (openCount >= AUTO_CASE_THRESHOLD) {
                    autoCreateCase(tenantId, alert);
                }
            }
        }

        // Auto-detect network links from event attributes
        detectNetworkLinks(tenantId, alerts, eventData);
    }

    boolean shouldAutoBlock(FraudAlert alert) {
        return alert.getSeverity() == AlertSeverity.CRITICAL
                && (alert.getAlertType() == AlertType.WATCHLIST_MATCH
                    || alert.getAlertType() == AlertType.STRUCTURING);
    }

    private void autoCreateCase(String tenantId, FraudAlert alert) {
        try {
            CreateCaseRequest req = new CreateCaseRequest();
            req.setTitle("Auto-generated: Multiple alerts for " + alert.getCustomerId());
            req.setDescription("Automatically created because customer has " + AUTO_CASE_THRESHOLD + "+ open fraud alerts");
            req.setCustomerId(alert.getCustomerId());
            req.setPriority("HIGH");
            req.setAlertIds(Set.of(alert.getId()));
            caseManagementService.createCase(req, tenantId);
            log.info("AUTO-CASE created for customer={}", alert.getCustomerId());
        } catch (Exception e) {
            log.warn("Failed to auto-create case for customer={}: {}", alert.getCustomerId(), e.getMessage());
        }
    }

    void detectNetworkLinks(String tenantId, List<FraudAlert> alerts, Map<String, Object> eventData) {
        if (eventData == null) return;
        String customerId = alerts.isEmpty() ? null : alerts.get(0).getCustomerId();
        if (customerId == null) return;

        Map<String, String> linkAttributes = Map.of(
                "phone", "SHARED_PHONE",
                "deviceId", "SHARED_DEVICE",
                "ipAddress", "SHARED_IP",
                "employer", "SHARED_EMPLOYER",
                "address", "SHARED_ADDRESS"
        );

        for (Map.Entry<String, String> attr : linkAttributes.entrySet()) {
            Object value = eventData.get(attr.getKey());
            if (value == null) continue;
            String linkValue = value.toString();

            var existingLinks = networkAnalysisService.findByLinkValue(tenantId, attr.getValue(), linkValue);
            Set<String> linkedCustomers = new HashSet<>();
            for (var link : existingLinks) {
                linkedCustomers.add(link.getCustomerIdA());
                linkedCustomers.add(link.getCustomerIdB());
            }
            linkedCustomers.remove(customerId);

            for (String otherCustomer : linkedCustomers) {
                networkAnalysisService.recordLink(tenantId, customerId, otherCustomer, attr.getValue(), linkValue);
                log.debug("Network link detected: {} <-> {} via {}={}", customerId, otherCustomer, attr.getValue(), linkValue);
            }
        }
    }
}
