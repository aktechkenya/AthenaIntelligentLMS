package com.athena.lms.fraud.service;

import com.athena.lms.common.event.EventTypes;
import com.athena.lms.fraud.config.FraudThresholdConfig;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.entity.FraudRule;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.*;
import com.athena.lms.fraud.repository.FraudRuleRepository;
import com.athena.lms.fraud.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    private final FraudRuleRepository ruleRepository;
    private final VelocityService velocityService;
    private final WatchlistRepository watchlistRepository;
    private final FraudThresholdConfig thresholdConfig;

    public List<FraudAlert> evaluate(String tenantId, String eventType, Map<String, Object> eventData) {
        List<FraudRule> rules = ruleRepository.findActiveRules(tenantId);
        List<FraudAlert> alerts = new ArrayList<>();

        String customerId = extractString(eventData, "customerId");
        BigDecimal amount = extractAmount(eventData);
        String subjectId = extractSubjectId(eventData);

        for (FraudRule rule : rules) {
            if (!rule.appliesTo(eventType)) continue;

            try {
                FraudAlert alert = evaluateRule(rule, tenantId, eventType, customerId, amount, subjectId, eventData);
                if (alert != null) {
                    alerts.add(alert);
                    log.info("Rule {} triggered for customer={} event={}", rule.getRuleCode(), customerId, eventType);
                }
            } catch (Exception e) {
                log.error("Error evaluating rule {}: {}", rule.getRuleCode(), e.getMessage(), e);
            }
        }

        return alerts;
    }

    private FraudAlert evaluateRule(FraudRule rule, String tenantId, String eventType,
                                     String customerId, BigDecimal amount, String subjectId,
                                     Map<String, Object> eventData) {
        return switch (rule.getRuleCode()) {
            case "LARGE_SINGLE_TXN" -> evaluateLargeTransaction(rule, tenantId, eventType, customerId, amount, subjectId);
            case "STRUCTURING" -> evaluateStructuring(rule, tenantId, eventType, customerId, amount, subjectId);
            case "HIGH_VELOCITY_1H" -> evaluateVelocity(rule, tenantId, eventType, customerId, amount, subjectId, 60);
            case "HIGH_VELOCITY_24H" -> evaluateVelocity(rule, tenantId, eventType, customerId, amount, subjectId, 1440);
            case "APPLICATION_STACKING" -> evaluateApplicationStacking(rule, tenantId, eventType, customerId, subjectId);
            case "RAPID_FUND_MOVEMENT" -> evaluateRapidFundMovement(rule, tenantId, eventType, customerId, amount, subjectId);
            case "ROUND_AMOUNT_PATTERN" -> evaluateRoundAmountPattern(rule, tenantId, eventType, customerId, amount, subjectId);
            case "WATCHLIST_MATCH" -> evaluateWatchlistMatch(rule, tenantId, eventType, customerId, subjectId, eventData);
            case "OVERPAYMENT" -> evaluateOverpayment(rule, tenantId, eventType, customerId, amount, subjectId, eventData);
            case "LOAN_CYCLING" -> evaluateLoanCycling(rule, tenantId, eventType, customerId, subjectId);
            case "DORMANT_REACTIVATION" -> evaluateDormantReactivation(rule, tenantId, eventType, customerId, subjectId, eventData);
            default -> null; // Unknown rules are skipped
        };
    }

    private FraudAlert evaluateLargeTransaction(FraudRule rule, String tenantId, String eventType,
                                                 String customerId, BigDecimal amount, String subjectId) {
        if (amount == null) return null;
        BigDecimal threshold = getParamDecimal(rule, "threshold", thresholdConfig.getLargeTransactionAmount());
        if (amount.compareTo(threshold) >= 0) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                    AlertType.LARGE_TRANSACTION,
                    String.format("Transaction of %s exceeds CTR threshold of %s", amount, threshold));
        }
        return null;
    }

    private FraudAlert evaluateStructuring(FraudRule rule, String tenantId, String eventType,
                                            String customerId, BigDecimal amount, String subjectId) {
        if (customerId == null || amount == null) return null;
        int windowHours = getParamInt(rule, "windowHours", thresholdConfig.getStructuringWindowHours());
        BigDecimal threshold = getParamDecimal(rule, "threshold", thresholdConfig.getStructuringThreshold());
        BigDecimal perTxnCeiling = getParamDecimal(rule, "perTxnCeiling", new BigDecimal("999999"));
        int minTxns = getParamInt(rule, "minTransactions", 3);

        // Only flag if individual transaction is below ceiling (potential structuring)
        if (amount.compareTo(perTxnCeiling) > 0) return null;

        BigDecimal totalAmount = velocityService.getTotalAmount(
                tenantId, customerId, "TXN_AMOUNT", windowHours * 60);
        int txnCount = velocityService.getCount(
                tenantId, customerId, "TXN_COUNT", windowHours * 60);

        if (totalAmount.add(amount).compareTo(threshold) >= 0 && txnCount >= minTxns) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                    AlertType.STRUCTURING,
                    String.format("Potential structuring: %d transactions totaling %s in %dh window (threshold: %s)",
                            txnCount + 1, totalAmount.add(amount), windowHours, threshold));
        }
        return null;
    }

    private FraudAlert evaluateVelocity(FraudRule rule, String tenantId, String eventType,
                                         String customerId, BigDecimal amount, String subjectId,
                                         int defaultWindowMinutes) {
        if (customerId == null) return null;
        int maxTxns = getParamInt(rule, "maxTransactions",
                defaultWindowMinutes == 60 ? thresholdConfig.getVelocityMaxTransactions1h()
                                           : thresholdConfig.getVelocityMaxTransactions24h());
        int windowMinutes = getParamInt(rule, "windowMinutes", defaultWindowMinutes);

        int count = velocityService.getCount(tenantId, customerId, "TXN_COUNT", windowMinutes);
        if (count >= maxTxns) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                    AlertType.HIGH_VELOCITY,
                    String.format("High velocity: %d transactions in %d-minute window (max: %d)",
                            count + 1, windowMinutes, maxTxns));
        }
        return null;
    }

    private FraudAlert evaluateApplicationStacking(FraudRule rule, String tenantId, String eventType,
                                                    String customerId, String subjectId) {
        if (customerId == null) return null;
        int maxApps = getParamInt(rule, "maxApplications", thresholdConfig.getVelocityMaxApplications30d());
        int windowDays = getParamInt(rule, "windowDays", 30);

        int count = velocityService.getCount(tenantId, customerId, "LOAN_APP", windowDays * 1440);
        if (count >= maxApps) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, null,
                    AlertType.APPLICATION_STACKING,
                    String.format("Application stacking: %d applications in %d days (max: %d)",
                            count + 1, windowDays, maxApps));
        }
        return null;
    }

    private FraudAlert evaluateRapidFundMovement(FraudRule rule, String tenantId, String eventType,
                                                   String customerId, BigDecimal amount, String subjectId) {
        if (customerId == null) return null;
        int windowMinutes = getParamInt(rule, "windowMinutes", thresholdConfig.getRapidTransferWindowMinutes());

        int creditCount = velocityService.getCount(tenantId, customerId, "CREDIT_RECEIVED", windowMinutes);
        int transferCount = velocityService.getCount(tenantId, customerId, "TRANSFER_OUT", windowMinutes);

        if (creditCount > 0 && transferCount > 0) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                    AlertType.RAPID_FUND_MOVEMENT,
                    String.format("Rapid fund movement: %d credits and %d transfers within %d minutes",
                            creditCount, transferCount + 1, windowMinutes));
        }
        return null;
    }

    private FraudAlert evaluateRoundAmountPattern(FraudRule rule, String tenantId, String eventType,
                                                    String customerId, BigDecimal amount, String subjectId) {
        if (customerId == null || amount == null) return null;
        BigDecimal roundThreshold = getParamDecimal(rule, "roundThreshold", new BigDecimal("10000"));

        if (amount.remainder(roundThreshold).compareTo(BigDecimal.ZERO) == 0) {
            int roundCount = velocityService.getCount(tenantId, customerId, "ROUND_AMOUNT", 1440);
            int minRound = getParamInt(rule, "minRoundTxns", 5);
            if (roundCount >= minRound) {
                return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                        AlertType.ROUND_AMOUNT_PATTERN,
                        String.format("Round amount pattern: %d round-number transactions in 24h", roundCount + 1));
            }
        }
        return null;
    }

    private FraudAlert evaluateWatchlistMatch(FraudRule rule, String tenantId, String eventType,
                                               String customerId, String subjectId, Map<String, Object> eventData) {
        String name = extractString(eventData, "fullName");
        String nationalId = extractString(eventData, "nationalId");
        String phone = extractString(eventData, "phone");

        if (name == null && nationalId == null && phone == null) return null;

        List<WatchlistEntry> matches = watchlistRepository.findMatches(
                tenantId,
                nationalId != null ? nationalId : "",
                name != null ? name : "",
                phone != null ? phone : "");

        if (!matches.isEmpty()) {
            WatchlistEntry match = matches.get(0);
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, null,
                    AlertType.WATCHLIST_MATCH,
                    String.format("Watchlist match: %s list (%s) — matched on %s. Source: %s",
                            match.getListType(), match.getEntryType(),
                            nationalId != null ? "national ID" : (name != null ? "name" : "phone"),
                            match.getSource()));
        }
        return null;
    }

    private FraudAlert evaluateOverpayment(FraudRule rule, String tenantId, String eventType,
                                            String customerId, BigDecimal amount, String subjectId,
                                            Map<String, Object> eventData) {
        if (amount == null) return null;
        BigDecimal outstandingBalance = extractDecimal(eventData, "outstandingBalance");
        if (outstandingBalance == null || outstandingBalance.compareTo(BigDecimal.ZERO) <= 0) return null;

        int thresholdPercent = getParamInt(rule, "overpaymentThresholdPercent", 110);
        BigDecimal threshold = outstandingBalance.multiply(new BigDecimal(thresholdPercent))
                .divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);

        if (amount.compareTo(threshold) > 0) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                    AlertType.OVERPAYMENT,
                    String.format("Overpayment: %s exceeds %d%% of outstanding balance %s",
                            amount, thresholdPercent, outstandingBalance));
        }
        return null;
    }

    private FraudAlert evaluateLoanCycling(FraudRule rule, String tenantId, String eventType,
                                            String customerId, String subjectId) {
        if (customerId == null) return null;
        int windowDays = getParamInt(rule, "windowDays", thresholdConfig.getLoanCyclingWindowDays());

        int closedLoans = velocityService.getCount(tenantId, customerId, "LOAN_CLOSED", windowDays * 1440);
        int newApps = velocityService.getCount(tenantId, customerId, "LOAN_APP", windowDays * 1440);

        if (closedLoans > 0 && newApps > 0) {
            return buildAlert(rule, tenantId, eventType, customerId, subjectId, null,
                    AlertType.LOAN_CYCLING,
                    String.format("Loan cycling: %d closed loans and %d new applications within %d days",
                            closedLoans, newApps + 1, windowDays));
        }
        return null;
    }

    private FraudAlert evaluateDormantReactivation(FraudRule rule, String tenantId, String eventType,
                                                     String customerId, String subjectId,
                                                     Map<String, Object> eventData) {
        if (customerId == null) return null;
        Object lastActivityObj = eventData.get("lastActivityDate");
        if (lastActivityObj == null) return null;

        // If event data includes account dormancy info, check it
        int dormantDays = getParamInt(rule, "dormantDays", thresholdConfig.getDormantAccountDays());
        BigDecimal amount = extractAmount(eventData);

        return buildAlert(rule, tenantId, eventType, customerId, subjectId, amount,
                AlertType.DORMANT_REACTIVATION,
                String.format("Activity on previously dormant account (dormant threshold: %d days)", dormantDays));
    }

    // ─── Rule CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FraudRule> getAllRules(String tenantId) {
        return ruleRepository.findByTenantIdOrGlobal(tenantId);
    }

    @Transactional(readOnly = true)
    public FraudRule getRule(UUID id, String tenantId) {
        return ruleRepository.findById(id)
            .filter(r -> r.getTenantId().equals(tenantId) || r.getTenantId().equals("*"))
            .orElseThrow(() -> new com.athena.lms.common.exception.ResourceNotFoundException("Rule not found: " + id));
    }

    public FraudRule updateRule(UUID id, com.athena.lms.fraud.dto.request.UpdateRuleRequest req, String tenantId) {
        FraudRule rule = getRule(id, tenantId);
        if (req.getSeverity() != null) rule.setSeverity(AlertSeverity.valueOf(req.getSeverity()));
        if (req.getEnabled() != null) rule.setEnabled(req.getEnabled());
        if (req.getParameters() != null) rule.setParameters(req.getParameters());
        return ruleRepository.save(rule);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private FraudAlert buildAlert(FraudRule rule, String tenantId, String eventType,
                                   String customerId, String subjectId, BigDecimal amount,
                                   AlertType alertType, String description) {
        AlertSeverity severity = rule.getSeverity();
        // Auto-escalate CRITICAL alerts
        boolean escalated = severity == AlertSeverity.CRITICAL;

        return FraudAlert.builder()
                .tenantId(tenantId)
                .alertType(alertType)
                .severity(severity)
                .status(AlertStatus.OPEN)
                .source(AlertSource.RULE_ENGINE)
                .ruleCode(rule.getRuleCode())
                .customerId(customerId)
                .subjectType(deriveSubjectType(eventType))
                .subjectId(subjectId != null ? subjectId : "unknown")
                .description(description)
                .triggerEvent(eventType)
                .triggerAmount(amount)
                .escalated(escalated)
                .build();
    }

    private String deriveSubjectType(String eventType) {
        if (eventType == null) return "UNKNOWN";
        if (eventType.startsWith("payment.") || eventType.startsWith("transfer.")) return "TRANSACTION";
        if (eventType.startsWith("loan.application")) return "APPLICATION";
        if (eventType.startsWith("loan.")) return "LOAN";
        if (eventType.startsWith("account.")) return "ACCOUNT";
        if (eventType.startsWith("overdraft.")) return "OVERDRAFT";
        if (eventType.startsWith("customer.")) return "CUSTOMER";
        if (eventType.startsWith("shop.")) return "ORDER";
        return "OTHER";
    }

    private String extractString(Map<String, Object> data, String key) {
        if (data == null) return null;
        // Check nested payload
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
        if (amt == null) amt = extractString(data, "triggerAmount");
        if (amt == null) amt = extractString(data, "totalAmount");
        if (amt == null) return null;
        try { return new BigDecimal(amt); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal extractDecimal(Map<String, Object> data, String key) {
        String val = extractString(data, key);
        if (val == null) return null;
        try { return new BigDecimal(val); } catch (NumberFormatException e) { return null; }
    }

    private String extractSubjectId(Map<String, Object> data) {
        String id = extractString(data, "paymentId");
        if (id != null) return id;
        id = extractString(data, "applicationId");
        if (id != null) return id;
        id = extractString(data, "loanId");
        if (id != null) return id;
        id = extractString(data, "transferId");
        if (id != null) return id;
        id = extractString(data, "accountId");
        if (id != null) return id;
        id = extractString(data, "customerId");
        return id != null ? id : "unknown";
    }

    private int getParamInt(FraudRule rule, String key, int defaultValue) {
        if (rule.getParameters() == null) return defaultValue;
        Object val = rule.getParameters().get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; } }
        return defaultValue;
    }

    private BigDecimal getParamDecimal(FraudRule rule, String key, BigDecimal defaultValue) {
        if (rule.getParameters() == null) return defaultValue;
        Object val = rule.getParameters().get(key);
        if (val instanceof Number n) return new BigDecimal(n.toString());
        if (val instanceof String s) { try { return new BigDecimal(s); } catch (NumberFormatException e) { return defaultValue; } }
        return defaultValue;
    }
}
