package com.athena.lms.fraud.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.fraud.dto.request.ResolveAlertRequest;
import com.athena.lms.fraud.dto.request.UpdateRuleRequest;
import com.athena.lms.fraud.dto.response.AlertResponse;
import com.athena.lms.fraud.dto.response.CustomerRiskResponse;
import com.athena.lms.fraud.dto.response.FraudSummaryResponse;
import com.athena.lms.fraud.dto.response.RuleResponse;
import com.athena.lms.fraud.entity.*;
import com.athena.lms.fraud.enums.*;
import com.athena.lms.fraud.event.FraudEventPublisher;
import com.athena.lms.fraud.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.athena.lms.fraud.ml.MLScoringClient;
import com.athena.lms.fraud.ml.MLScoringResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final FraudAlertRepository alertRepository;
    private final FraudEventRepository eventRepository;
    private final CustomerRiskProfileRepository riskProfileRepository;
    private final RuleEngineService ruleEngineService;
    private final VelocityService velocityService;
    private final FraudEventPublisher eventPublisher;
    private final MLScoringClient mlScoringClient;
    private final AutoActionService autoActionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FraudScoringService fraudScoringService;

    // ─── Core Processing ─────────────────────────────────────────────────────────

    public List<FraudAlert> processEvent(String tenantId, String eventType, Map<String, Object> eventData) {
        String customerId = extractString(eventData, "customerId");
        BigDecimal amount = extractAmount(eventData);

        // 1. Update velocity counters
        updateVelocityCounters(tenantId, customerId, eventType, amount);

        // 2. Run rule engine
        List<FraudAlert> triggeredAlerts = ruleEngineService.evaluate(tenantId, eventType, eventData);

        // 3. Get ML score (non-blocking — falls back to rules if unavailable)
        BigDecimal mlScore = null;
        String mlModelVersion = null;
        if (customerId != null) {
            double ruleScore = triggeredAlerts.isEmpty() ? 0.0 : 0.7; // rule triggered = baseline 0.7
            MLScoringResponse mlResult = mlScoringClient.scoreCombined(
                tenantId, customerId, eventType, amount, ruleScore);
            if (mlResult != null) {
                mlScore = BigDecimal.valueOf(mlResult.getScore());
                mlModelVersion = mlResult.isModelAvailable() ? "combined-v1" : "rules-only";
            }
        }

        // 4. Persist alerts and update risk profiles
        List<FraudAlert> savedAlerts = new ArrayList<>();
        for (FraudAlert alert : triggeredAlerts) {
            // Dedup: skip if same rule triggered for same customer in last hour
            long recentCount = alertRepository.countRecentAlertsByRule(
                    tenantId, alert.getCustomerId(), alert.getRuleCode(),
                    OffsetDateTime.now().minusHours(1));
            if (recentCount > 0) {
                log.debug("Skipping duplicate alert: rule={} customer={}", alert.getRuleCode(), alert.getCustomerId());
                continue;
            }

            // Enrich with ML score if available
            if (mlScore != null) {
                alert.setRiskScore(mlScore);
                alert.setModelVersion(mlModelVersion);
            }

            FraudAlert saved = alertRepository.save(alert);
            savedAlerts.add(saved);

            // Publish event
            eventPublisher.publishFraudAlertRaised(saved);

            // Auto-escalate HIGH/CRITICAL AML-related alerts to compliance
            if (shouldEscalateToCompliance(saved)) {
                saved.setEscalatedToCompliance(true);
                alertRepository.save(saved);
                eventPublisher.escalateToCompliance(saved);
            }

            // Update customer risk profile
            if (customerId != null) {
                updateRiskProfile(tenantId, customerId, saved);
            }
        }

        // 5. Log the event
        String rulesTriggered = savedAlerts.stream()
                .map(FraudAlert::getRuleCode)
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);

        BigDecimal riskScore = savedAlerts.stream()
                .map(FraudAlert::getRiskScore)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);

        FraudEvent fraudEvent = FraudEvent.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .sourceService(extractString(eventData, "source"))
                .customerId(customerId)
                .subjectId(extractSubjectId(eventData))
                .amount(amount)
                .riskScore(riskScore)
                .rulesTriggered(rulesTriggered)
                .payload(eventData)
                .build();
        eventRepository.save(fraudEvent);

        // 6. Run auto-actions (network link detection, auto-block, auto-case-creation)
        if (!savedAlerts.isEmpty()) {
            try {
                autoActionService.processAutoActions(tenantId, savedAlerts, eventData);
            } catch (Exception e) {
                log.warn("Auto-action processing failed: {}", e.getMessage());
            }
        }

        return savedAlerts;
    }

    private void updateVelocityCounters(String tenantId, String customerId, String eventType, BigDecimal amount) {
        if (customerId == null) return;

        // Transaction count (hourly buckets)
        velocityService.increment(tenantId, customerId, "TXN_COUNT", amount, 60);

        // Event-type-specific counters
        switch (eventType) {
            case "payment.completed", "account.credit.received" ->
                velocityService.increment(tenantId, customerId, "CREDIT_RECEIVED", amount, 60);
            case "transfer.completed", "mobile.transfer.completed" ->
                velocityService.increment(tenantId, customerId, "TRANSFER_OUT", amount, 60);
            case "loan.application.submitted" ->
                velocityService.increment(tenantId, customerId, "LOAN_APP", BigDecimal.ZERO, 1440);
            case "loan.closed" ->
                velocityService.increment(tenantId, customerId, "LOAN_CLOSED", BigDecimal.ZERO, 1440);
            case "payment.reversed" ->
                velocityService.increment(tenantId, customerId, "PAYMENT_REVERSED", amount, 1440);
        }

        // Round amount tracking
        if (amount != null && amount.remainder(new BigDecimal("10000")).compareTo(BigDecimal.ZERO) == 0) {
            velocityService.increment(tenantId, customerId, "ROUND_AMOUNT", amount, 1440);
        }

        // Running transaction amount for structuring detection
        if (amount != null) {
            velocityService.increment(tenantId, customerId, "TXN_AMOUNT", amount, 60);
        }
    }

    private boolean shouldEscalateToCompliance(FraudAlert alert) {
        if (alert.getSeverity() == AlertSeverity.CRITICAL) return true;
        if (alert.getSeverity() == AlertSeverity.HIGH) {
            return alert.getAlertType() == AlertType.STRUCTURING
                || alert.getAlertType() == AlertType.WATCHLIST_MATCH
                || alert.getAlertType() == AlertType.LOAN_CYCLING
                || alert.getAlertType() == AlertType.RAPID_FUND_MOVEMENT;
        }
        return false;
    }

    private void updateRiskProfile(String tenantId, String customerId, FraudAlert alert) {
        CustomerRiskProfile profile = riskProfileRepository
                .findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseGet(() -> CustomerRiskProfile.builder()
                        .tenantId(tenantId)
                        .customerId(customerId)
                        .build());

        profile.setTotalAlerts(profile.getTotalAlerts() + 1);
        profile.setOpenAlerts(profile.getOpenAlerts() + 1);
        profile.setLastAlertAt(OffsetDateTime.now());

        // Recalculate risk level based on alert count and severity
        recalculateRiskLevel(profile);
        riskProfileRepository.save(profile);
    }

    private void recalculateRiskLevel(CustomerRiskProfile profile) {
        int score = 0;
        score += profile.getOpenAlerts() * 10;
        score += profile.getConfirmedFraud() * 50;
        score -= profile.getFalsePositives() * 5;
        score = Math.max(0, Math.min(100, score));

        BigDecimal normalizedScore = new BigDecimal(score).divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
        profile.setRiskScore(normalizedScore);

        if (score >= 70) profile.setRiskLevel(RiskLevel.CRITICAL);
        else if (score >= 50) profile.setRiskLevel(RiskLevel.HIGH);
        else if (score >= 25) profile.setRiskLevel(RiskLevel.MEDIUM);
        else profile.setRiskLevel(RiskLevel.LOW);
    }

    // ─── Alert Management ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AlertResponse getAlert(UUID id, String tenantId) {
        FraudAlert alert = alertRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Fraud alert not found: " + id));
        return mapToAlertResponse(alert);
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> listAlerts(String tenantId, AlertStatus status, Pageable pageable) {
        Page<FraudAlert> page = (status != null)
                ? alertRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : alertRepository.findByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(this::mapToAlertResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> listCustomerAlerts(String tenantId, String customerId, Pageable pageable) {
        Page<FraudAlert> page = alertRepository.findByTenantIdAndCustomerId(tenantId, customerId, pageable);
        return PageResponse.from(page.map(this::mapToAlertResponse));
    }

    public AlertResponse resolveAlert(UUID id, ResolveAlertRequest req, String tenantId) {
        FraudAlert alert = alertRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Fraud alert not found: " + id));

        AlertStatus newStatus = req.getConfirmedFraud() != null && req.getConfirmedFraud()
                ? AlertStatus.CONFIRMED_FRAUD : AlertStatus.FALSE_POSITIVE;

        alert.setStatus(newStatus);
        alert.setResolvedBy(req.getResolvedBy());
        alert.setResolvedAt(OffsetDateTime.now());
        alert.setResolution(newStatus.name());
        alert.setResolutionNotes(req.getNotes());
        alert = alertRepository.save(alert);

        // Update risk profile
        if (alert.getCustomerId() != null) {
            CustomerRiskProfile profile = riskProfileRepository
                    .findByTenantIdAndCustomerId(tenantId, alert.getCustomerId())
                    .orElse(null);
            if (profile != null) {
                profile.setOpenAlerts(Math.max(0, profile.getOpenAlerts() - 1));
                if (newStatus == AlertStatus.CONFIRMED_FRAUD) {
                    profile.setConfirmedFraud(profile.getConfirmedFraud() + 1);
                } else {
                    profile.setFalsePositives(profile.getFalsePositives() + 1);
                }
                recalculateRiskLevel(profile);
                riskProfileRepository.save(profile);
            }
        }

        log.info("Resolved fraud alert id={} status={} by={}", id, newStatus, req.getResolvedBy());
        return mapToAlertResponse(alert);
    }

    public AlertResponse assignAlert(UUID id, String assignee, String tenantId) {
        FraudAlert alert = alertRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Fraud alert not found: " + id));
        alert.setAssignedTo(assignee);
        alert.setStatus(AlertStatus.UNDER_REVIEW);
        alert = alertRepository.save(alert);
        return mapToAlertResponse(alert);
    }

    // ─── Customer Risk ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerRiskResponse getCustomerRisk(String tenantId, String customerId) {
        CustomerRiskProfile profile = riskProfileRepository
                .findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseGet(() -> CustomerRiskProfile.builder()
                        .tenantId(tenantId)
                        .customerId(customerId)
                        .riskLevel(RiskLevel.LOW)
                        .riskScore(BigDecimal.ZERO)
                        .build());
        return mapToRiskResponse(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerRiskResponse> listHighRiskCustomers(String tenantId, Pageable pageable) {
        Page<CustomerRiskProfile> page = riskProfileRepository
                .findByTenantIdAndRiskLevel(tenantId, RiskLevel.HIGH, pageable);
        return PageResponse.from(page.map(this::mapToRiskResponse));
    }

    // ─── Summary ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FraudSummaryResponse getSummary(String tenantId) {
        FraudSummaryResponse summary = new FraudSummaryResponse();
        summary.setTenantId(tenantId);
        summary.setOpenAlerts(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.OPEN));
        summary.setUnderReviewAlerts(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.UNDER_REVIEW));
        summary.setEscalatedAlerts(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.ESCALATED));
        summary.setConfirmedFraud(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.CONFIRMED_FRAUD));
        summary.setCriticalAlerts(alertRepository.countByTenantIdAndSeverityAndStatus(
                tenantId, AlertSeverity.CRITICAL, AlertStatus.OPEN));
        summary.setHighRiskCustomers(riskProfileRepository.countByTenantIdAndRiskLevel(tenantId, RiskLevel.HIGH));
        summary.setCriticalRiskCustomers(riskProfileRepository.countByTenantIdAndRiskLevel(tenantId, RiskLevel.CRITICAL));
        return summary;
    }

    // ─── Rules Management ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RuleResponse> listRules(String tenantId) {
        // Return global rules ('*') and tenant-specific rules
        List<FraudRule> rules = ruleEngineService.getAllRules(tenantId);
        return rules.stream().map(this::mapToRuleResponse).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse getRule(UUID id, String tenantId) {
        FraudRule rule = ruleEngineService.getRule(id, tenantId);
        return mapToRuleResponse(rule);
    }

    public RuleResponse updateRule(UUID id, UpdateRuleRequest req, String tenantId) {
        FraudRule rule = ruleEngineService.updateRule(id, req, tenantId);
        return mapToRuleResponse(rule);
    }

    // ─── Bulk Operations ──────────────────────────────────────────────────────

    public Map<String, Object> bulkAssign(Set<UUID> alertIds, String assignee, String performedBy, String tenantId) {
        int assigned = 0;
        for (UUID id : alertIds) {
            try {
                assignAlert(id, assignee, tenantId);
                assigned++;
            } catch (Exception e) {
                log.warn("Failed to assign alert {}: {}", id, e.getMessage());
            }
        }
        return Map.of("assigned", assigned, "total", alertIds.size());
    }

    public Map<String, Object> bulkResolve(Set<UUID> alertIds, boolean confirmedFraud, String performedBy, String notes, String tenantId) {
        int resolved = 0;
        for (UUID id : alertIds) {
            try {
                ResolveAlertRequest req = new ResolveAlertRequest();
                req.setConfirmedFraud(confirmedFraud);
                req.setResolvedBy(performedBy);
                req.setNotes(notes);
                resolveAlert(id, req, tenantId);
                resolved++;
            } catch (Exception e) {
                log.warn("Failed to resolve alert {}: {}", id, e.getMessage());
            }
        }
        return Map.of("resolved", resolved, "total", alertIds.size());
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────────

    private AlertResponse mapToAlertResponse(FraudAlert alert) {
        AlertResponse resp = new AlertResponse();
        resp.setId(alert.getId());
        resp.setTenantId(alert.getTenantId());
        resp.setAlertType(alert.getAlertType());
        resp.setSeverity(alert.getSeverity());
        resp.setStatus(alert.getStatus());
        resp.setSource(alert.getSource());
        resp.setRuleCode(alert.getRuleCode());
        resp.setCustomerId(alert.getCustomerId());
        resp.setSubjectType(alert.getSubjectType());
        resp.setSubjectId(alert.getSubjectId());
        resp.setDescription(alert.getDescription());
        resp.setTriggerEvent(alert.getTriggerEvent());
        resp.setTriggerAmount(alert.getTriggerAmount());
        resp.setRiskScore(alert.getRiskScore());
        resp.setEscalated(alert.getEscalated());
        resp.setEscalatedToCompliance(alert.getEscalatedToCompliance());
        resp.setAssignedTo(alert.getAssignedTo());
        resp.setResolvedBy(alert.getResolvedBy());
        resp.setResolvedAt(alert.getResolvedAt());
        resp.setResolution(alert.getResolution());
        resp.setResolutionNotes(alert.getResolutionNotes());
        resp.setExplanation(alert.getExplanation());
        resp.setCreatedAt(alert.getCreatedAt());
        resp.setUpdatedAt(alert.getUpdatedAt());
        return resp;
    }

    private CustomerRiskResponse mapToRiskResponse(CustomerRiskProfile profile) {
        CustomerRiskResponse resp = new CustomerRiskResponse();
        resp.setCustomerId(profile.getCustomerId());
        resp.setTenantId(profile.getTenantId());
        resp.setRiskScore(profile.getRiskScore());
        resp.setRiskLevel(profile.getRiskLevel());
        resp.setTotalAlerts(profile.getTotalAlerts());
        resp.setOpenAlerts(profile.getOpenAlerts());
        resp.setConfirmedFraud(profile.getConfirmedFraud());
        resp.setFalsePositives(profile.getFalsePositives());
        resp.setLastAlertAt(profile.getLastAlertAt());
        resp.setFactors(profile.getFactors());
        return resp;
    }

    private RuleResponse mapToRuleResponse(FraudRule rule) {
        RuleResponse resp = new RuleResponse();
        resp.setId(rule.getId());
        resp.setRuleCode(rule.getRuleCode());
        resp.setRuleName(rule.getRuleName());
        resp.setDescription(rule.getDescription());
        resp.setCategory(rule.getCategory());
        resp.setSeverity(rule.getSeverity());
        resp.setEventTypes(rule.getEventTypes());
        resp.setEnabled(rule.getEnabled());
        resp.setParameters(rule.getParameters());
        resp.setCreatedAt(rule.getCreatedAt());
        resp.setUpdatedAt(rule.getUpdatedAt());
        return resp;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object val = data.get(key);
        if (val != null) return val.toString();
        Object payload = data.get("payload");
        if (payload instanceof Map<?, ?> p) {
            Object nested = p.get(key);
            if (nested != null) return nested.toString();
        }
        return null;
    }

    private BigDecimal extractAmount(Map<String, Object> data) {
        String amt = extractString(data, "amount");
        if (amt == null) return null;
        try { return new BigDecimal(amt); } catch (NumberFormatException e) { return null; }
    }

    private String extractSubjectId(Map<String, Object> data) {
        for (String key : List.of("paymentId", "applicationId", "loanId", "transferId", "accountId")) {
            String val = extractString(data, key);
            if (val != null) return val;
        }
        return extractString(data, "customerId");
    }
}
