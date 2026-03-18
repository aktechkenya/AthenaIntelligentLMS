package com.athena.lms.fraud.service;

import com.athena.lms.fraud.config.FraudThresholdConfig;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.entity.FraudRule;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.AlertType;
import com.athena.lms.fraud.enums.RuleCategory;
import com.athena.lms.fraud.repository.FraudRuleRepository;
import com.athena.lms.fraud.repository.WatchlistRepository;
// Note: RuleEngineService only depends on FraudRuleRepository (not FraudAlertRepository)
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTest {

    @Mock private FraudRuleRepository ruleRepository;
    @Mock private VelocityService velocityService;
    @Mock private WatchlistRepository watchlistRepository;
    @Mock private FraudThresholdConfig thresholdConfig;

    @InjectMocks private RuleEngineService ruleEngineService;

    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        // Default thresholds
        lenient().when(thresholdConfig.getLargeTransactionAmount()).thenReturn(new BigDecimal("1000000"));
        lenient().when(thresholdConfig.getStructuringThreshold()).thenReturn(new BigDecimal("1000000"));
        lenient().when(thresholdConfig.getStructuringWindowHours()).thenReturn(24);
        lenient().when(thresholdConfig.getVelocityMaxTransactions1h()).thenReturn(10);
        lenient().when(thresholdConfig.getVelocityMaxTransactions24h()).thenReturn(50);
        lenient().when(thresholdConfig.getVelocityMaxApplications30d()).thenReturn(5);
        lenient().when(thresholdConfig.getRapidTransferWindowMinutes()).thenReturn(15);
        lenient().when(thresholdConfig.getDormantAccountDays()).thenReturn(180);
        lenient().when(thresholdConfig.getLoanCyclingWindowDays()).thenReturn(7);
    }

    private FraudRule makeRule(String code, String category, String severity, String... eventTypes) {
        FraudRule rule = new FraudRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(TENANT);
        rule.setRuleCode(code);
        rule.setRuleName(code);
        rule.setCategory(RuleCategory.valueOf(category));
        rule.setSeverity(AlertSeverity.valueOf(severity));
        rule.setEventTypes(String.join(",", eventTypes));
        rule.setEnabled(true);
        rule.setParameters(new HashMap<>());
        return rule;
    }

    private Map<String, Object> eventData(String customerId, BigDecimal amount) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerId", customerId);
        if (amount != null) data.put("amount", amount.toString());
        return data;
    }

    @Nested
    @DisplayName("LARGE_SINGLE_TXN Rule")
    class LargeTransactionTests {

        @Test
        @DisplayName("triggers when amount exceeds threshold")
        void shouldTriggerForLargeAmount() {
            FraudRule rule = makeRule("LARGE_SINGLE_TXN", "TRANSACTION", "HIGH", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("2000000")));

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.LARGE_TRANSACTION);
            assertThat(alerts.get(0).getTriggerAmount()).isEqualByComparingTo(new BigDecimal("2000000"));
        }

        @Test
        @DisplayName("does not trigger for amount below threshold")
        void shouldNotTriggerForSmallAmount() {
            FraudRule rule = makeRule("LARGE_SINGLE_TXN", "TRANSACTION", "HIGH", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("500000")));

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("does not trigger for non-matching event type")
        void shouldNotTriggerForWrongEventType() {
            FraudRule rule = makeRule("LARGE_SINGLE_TXN", "TRANSACTION", "HIGH", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "loan.application.submitted", eventData("CUST-1", new BigDecimal("2000000")));

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("HIGH_VELOCITY_1H Rule")
    class HighVelocity1hTests {

        @Test
        @DisplayName("triggers when transaction count exceeds 1h limit")
        void shouldTriggerForHighVelocity() {
            FraudRule rule = makeRule("HIGH_VELOCITY_1H", "VELOCITY", "HIGH", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));
            when(velocityService.getCount(eq(TENANT), eq("CUST-1"), eq("TXN_COUNT"), eq(60)))
                .thenReturn(15);

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("50000")));

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.HIGH_VELOCITY);
        }

        @Test
        @DisplayName("does not trigger when count is below limit")
        void shouldNotTriggerBelowLimit() {
            FraudRule rule = makeRule("HIGH_VELOCITY_1H", "VELOCITY", "HIGH", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));
            when(velocityService.getCount(eq(TENANT), eq("CUST-1"), eq("TXN_COUNT"), eq(60)))
                .thenReturn(5);

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("50000")));

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("STRUCTURING Rule")
    class StructuringTests {

        @Test
        @DisplayName("triggers when cumulative amount approaches threshold via many small txns")
        void shouldTriggerForStructuring() {
            FraudRule rule = makeRule("STRUCTURING", "AML", "CRITICAL", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));
            // 24h cumulative = 950000, current txn = 100000 → total 1050000 > threshold
            when(velocityService.getTotalAmount(eq(TENANT), eq("CUST-1"), eq("TXN_AMOUNT"), eq(1440)))
                .thenReturn(new BigDecimal("950000"));
            // Need at least minTransactions (default 3)
            when(velocityService.getCount(eq(TENANT), eq("CUST-1"), eq("TXN_COUNT"), eq(1440)))
                .thenReturn(5);

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("100000")));

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.STRUCTURING);
            assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("APPLICATION_STACKING Rule")
    class ApplicationStackingTests {

        @Test
        @DisplayName("triggers when loan applications exceed 30d limit")
        void shouldTriggerForStacking() {
            FraudRule rule = makeRule("APPLICATION_STACKING", "APPLICATION", "HIGH",
                "loan.application.submitted");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));
            when(velocityService.getCount(eq(TENANT), eq("CUST-1"), eq("LOAN_APP"), eq(43200)))
                .thenReturn(6);

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "loan.application.submitted", eventData("CUST-1", null));

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.APPLICATION_STACKING);
        }
    }

    @Nested
    @DisplayName("WATCHLIST_MATCH Rule")
    class WatchlistTests {

        @Test
        @DisplayName("triggers when customer is on watchlist")
        void shouldTriggerForWatchlistMatch() {
            FraudRule rule = makeRule("WATCHLIST_MATCH", "COMPLIANCE", "CRITICAL",
                "customer.created", "customer.updated", "loan.application.submitted");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));

            WatchlistEntry entry = new WatchlistEntry();
            entry.setListType(com.athena.lms.fraud.enums.WatchlistType.SANCTIONS);
            entry.setEntryType("INDIVIDUAL");
            entry.setSource("Test");
            when(watchlistRepository.findMatches(eq(TENANT), eq("12345"), anyString(), anyString()))
                .thenReturn(List.of(entry));

            Map<String, Object> data = eventData("CUST-1", new BigDecimal("50000"));
            data.put("nationalId", "12345");

            List<FraudAlert> alerts = ruleEngineService.evaluate(TENANT, "customer.created", data);

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.WATCHLIST_MATCH);
            assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("ROUND_AMOUNT_PATTERN Rule")
    class RoundAmountTests {

        @Test
        @DisplayName("triggers when many round-amount transactions detected")
        void shouldTriggerForRoundAmounts() {
            FraudRule rule = makeRule("ROUND_AMOUNT_PATTERN", "AML", "MEDIUM", "payment.completed");
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(rule));
            when(velocityService.getCount(eq(TENANT), eq("CUST-1"), eq("ROUND_AMOUNT"), eq(1440)))
                .thenReturn(6);

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("100000")));

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.ROUND_AMOUNT_PATTERN);
        }
    }

    @Nested
    @DisplayName("Disabled Rules")
    class DisabledRuleTests {

        @Test
        @DisplayName("disabled rules are not returned by repository")
        void shouldNotEvaluateDisabledRules() {
            // No active rules returned
            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of());

            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("5000000")));

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("Multiple Rules Firing")
    class MultipleRuleTests {

        @Test
        @DisplayName("multiple rules can trigger on the same event")
        void shouldTriggerMultipleRules() {
            FraudRule largeRule = makeRule("LARGE_SINGLE_TXN", "TRANSACTION", "HIGH", "payment.completed");
            FraudRule roundRule = makeRule("ROUND_AMOUNT_PATTERN", "AML", "MEDIUM", "payment.completed");

            when(ruleRepository.findActiveRules(TENANT)).thenReturn(List.of(largeRule, roundRule));
            // Round amount velocity count triggers
            when(velocityService.getCount(eq(TENANT), eq("CUST-1"), eq("ROUND_AMOUNT"), eq(1440)))
                .thenReturn(6);

            // Amount is both large AND round
            List<FraudAlert> alerts = ruleEngineService.evaluate(
                TENANT, "payment.completed", eventData("CUST-1", new BigDecimal("2000000")));

            assertThat(alerts).hasSize(2);
            assertThat(alerts).extracting(FraudAlert::getAlertType)
                .containsExactlyInAnyOrder(AlertType.LARGE_TRANSACTION, AlertType.ROUND_AMOUNT_PATTERN);
        }
    }
}
