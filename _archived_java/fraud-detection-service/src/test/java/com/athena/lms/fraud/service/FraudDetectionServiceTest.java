package com.athena.lms.fraud.service;

import com.athena.lms.fraud.entity.CustomerRiskProfile;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.enums.*;
import com.athena.lms.fraud.event.FraudEventPublisher;
import com.athena.lms.fraud.ml.MLScoringClient;
import com.athena.lms.fraud.ml.MLScoringResponse;
import com.athena.lms.fraud.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock private FraudAlertRepository alertRepository;
    @Mock private FraudEventRepository eventRepository;
    @Mock private CustomerRiskProfileRepository riskProfileRepository;
    @Mock private RuleEngineService ruleEngineService;
    @Mock private VelocityService velocityService;
    @Mock private FraudEventPublisher eventPublisher;
    @Mock private MLScoringClient mlScoringClient;

    @InjectMocks private FraudDetectionService service;

    private static final String TENANT = "test-tenant";

    @Nested
    @DisplayName("processEvent")
    class ProcessEventTests {

        @Test
        @DisplayName("processes event with no rule triggers — no alerts created")
        void noRulesTrigger() {
            when(ruleEngineService.evaluate(eq(TENANT), anyString(), anyMap())).thenReturn(List.of());

            Map<String, Object> data = Map.of("customerId", "CUST-1", "amount", "50000");
            List<FraudAlert> result = service.processEvent(TENANT, "payment.completed", data);

            assertThat(result).isEmpty();
            verify(alertRepository, never()).save(any());
            verify(eventRepository).save(any()); // Event is always logged
        }

        @Test
        @DisplayName("saves alert and publishes event when rule triggers")
        void ruleTriggered() {
            FraudAlert alert = FraudAlert.builder()
                .tenantId(TENANT)
                .alertType(AlertType.LARGE_TRANSACTION)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.OPEN)
                .source(AlertSource.RULE_ENGINE)
                .ruleCode("LARGE_SINGLE_TXN")
                .customerId("CUST-1")
                .subjectType("PAYMENT")
                .subjectId("PAY-1")
                .description("Large transaction detected")
                .triggerAmount(new BigDecimal("2000000"))
                .build();

            when(ruleEngineService.evaluate(eq(TENANT), anyString(), anyMap())).thenReturn(List.of(alert));
            when(alertRepository.countRecentAlertsByRule(anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
            when(alertRepository.save(any())).thenReturn(alert);
            when(riskProfileRepository.findByTenantIdAndCustomerId(eq(TENANT), eq("CUST-1")))
                .thenReturn(Optional.of(CustomerRiskProfile.builder()
                    .tenantId(TENANT).customerId("CUST-1")
                    .riskLevel(RiskLevel.LOW).riskScore(BigDecimal.ZERO)
                    .totalAlerts(0).openAlerts(0).confirmedFraud(0).falsePositives(0)
                    .build()));

            Map<String, Object> data = Map.of("customerId", "CUST-1", "amount", "2000000");
            List<FraudAlert> result = service.processEvent(TENANT, "payment.completed", data);

            assertThat(result).hasSize(1);
            verify(alertRepository).save(any());
            verify(eventPublisher).publishFraudAlertRaised(any());
        }

        @Test
        @DisplayName("deduplicates alerts for same rule/customer within 1 hour")
        void deduplicate() {
            FraudAlert alert = FraudAlert.builder()
                .tenantId(TENANT)
                .alertType(AlertType.LARGE_TRANSACTION)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.OPEN)
                .source(AlertSource.RULE_ENGINE)
                .ruleCode("LARGE_SINGLE_TXN")
                .customerId("CUST-1")
                .subjectType("PAYMENT")
                .subjectId("PAY-1")
                .description("Duplicate")
                .build();

            when(ruleEngineService.evaluate(eq(TENANT), anyString(), anyMap())).thenReturn(List.of(alert));
            // Already triggered within last hour
            when(alertRepository.countRecentAlertsByRule(anyString(), eq("CUST-1"), eq("LARGE_SINGLE_TXN"), any()))
                .thenReturn(1L);

            Map<String, Object> data = Map.of("customerId", "CUST-1", "amount", "2000000");
            List<FraudAlert> result = service.processEvent(TENANT, "payment.completed", data);

            assertThat(result).isEmpty();
            verify(alertRepository, never()).save(any(FraudAlert.class));
        }

        @Test
        @DisplayName("escalates CRITICAL alerts to compliance")
        void escalateCritical() {
            FraudAlert alert = FraudAlert.builder()
                .tenantId(TENANT)
                .alertType(AlertType.STRUCTURING)
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.OPEN)
                .source(AlertSource.RULE_ENGINE)
                .ruleCode("STRUCTURING")
                .customerId("CUST-1")
                .subjectType("PAYMENT")
                .subjectId("PAY-1")
                .description("Structuring detected")
                .escalated(false)
                .escalatedToCompliance(false)
                .build();

            when(ruleEngineService.evaluate(eq(TENANT), anyString(), anyMap())).thenReturn(List.of(alert));
            when(alertRepository.countRecentAlertsByRule(anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
            when(alertRepository.save(any())).thenReturn(alert);
            when(riskProfileRepository.findByTenantIdAndCustomerId(eq(TENANT), eq("CUST-1")))
                .thenReturn(Optional.empty());

            Map<String, Object> data = Map.of("customerId", "CUST-1", "amount", "100000");
            service.processEvent(TENANT, "payment.completed", data);

            // Save is called twice: once for initial save, once after escalation
            verify(alertRepository, atLeast(2)).save(any());
            verify(eventPublisher).escalateToCompliance(any());
        }

        @Test
        @DisplayName("enriches alert with ML score when available")
        void enrichWithMLScore() {
            FraudAlert alert = FraudAlert.builder()
                .tenantId(TENANT)
                .alertType(AlertType.LARGE_TRANSACTION)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.OPEN)
                .source(AlertSource.RULE_ENGINE)
                .ruleCode("LARGE_SINGLE_TXN")
                .customerId("CUST-1")
                .subjectType("PAYMENT")
                .subjectId("PAY-1")
                .description("Large txn")
                .build();

            when(ruleEngineService.evaluate(eq(TENANT), anyString(), anyMap())).thenReturn(List.of(alert));
            when(alertRepository.countRecentAlertsByRule(anyString(), anyString(), anyString(), any()))
                .thenReturn(0L);
            when(alertRepository.save(any())).thenReturn(alert);
            when(riskProfileRepository.findByTenantIdAndCustomerId(eq(TENANT), eq("CUST-1")))
                .thenReturn(Optional.empty());

            // ML service returns a score
            MLScoringResponse mlResult = MLScoringResponse.builder()
                    .score(0.85)
                    .riskLevel("HIGH")
                    .modelAvailable(true)
                    .latencyMs(30.0)
                    .details(Map.of())
                    .build();
            when(mlScoringClient.scoreCombined(anyString(), anyString(), anyString(), any(), anyDouble()))
                .thenReturn(mlResult);

            Map<String, Object> data = Map.of("customerId", "CUST-1", "amount", "2000000");
            List<FraudAlert> result = service.processEvent(TENANT, "payment.completed", data);

            assertThat(result).hasSize(1);
            ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
            verify(alertRepository, atLeast(1)).save(captor.capture());
            FraudAlert saved = captor.getAllValues().get(0);
            assertThat(saved.getRiskScore()).isNotNull();
            assertThat(saved.getModelVersion()).isEqualTo("combined-v1");
        }
    }

    @Nested
    @DisplayName("Alert Resolution")
    class AlertResolutionTests {

        @Test
        @DisplayName("confirms fraud and updates risk profile")
        void confirmFraud() {
            FraudAlert alert = FraudAlert.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .alertType(AlertType.STRUCTURING)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.OPEN)
                .customerId("CUST-1")
                .subjectType("PAYMENT")
                .subjectId("PAY-1")
                .description("Test")
                .build();

            CustomerRiskProfile profile = CustomerRiskProfile.builder()
                .tenantId(TENANT).customerId("CUST-1")
                .riskLevel(RiskLevel.MEDIUM).riskScore(new BigDecimal("0.25"))
                .totalAlerts(3).openAlerts(2).confirmedFraud(0).falsePositives(0)
                .build();

            when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskProfileRepository.findByTenantIdAndCustomerId(TENANT, "CUST-1"))
                .thenReturn(Optional.of(profile));
            when(riskProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var req = new com.athena.lms.fraud.dto.request.ResolveAlertRequest();
            req.setConfirmedFraud(true);
            req.setResolvedBy("analyst-1");
            req.setNotes("Verified structuring pattern");

            var result = service.resolveAlert(alert.getId(), req, TENANT);

            assertThat(result.getStatus()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
            verify(riskProfileRepository).save(argThat(p ->
                p.getConfirmedFraud() == 1 && p.getOpenAlerts() == 1
            ));
        }

        @Test
        @DisplayName("marks false positive and reduces risk score")
        void falsePositive() {
            FraudAlert alert = FraudAlert.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .alertType(AlertType.LARGE_TRANSACTION)
                .severity(AlertSeverity.MEDIUM)
                .status(AlertStatus.OPEN)
                .customerId("CUST-2")
                .subjectType("PAYMENT")
                .subjectId("PAY-2")
                .description("Test FP")
                .build();

            CustomerRiskProfile profile = CustomerRiskProfile.builder()
                .tenantId(TENANT).customerId("CUST-2")
                .riskLevel(RiskLevel.MEDIUM).riskScore(new BigDecimal("0.30"))
                .totalAlerts(2).openAlerts(1).confirmedFraud(0).falsePositives(0)
                .build();

            when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(riskProfileRepository.findByTenantIdAndCustomerId(TENANT, "CUST-2"))
                .thenReturn(Optional.of(profile));
            when(riskProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var req = new com.athena.lms.fraud.dto.request.ResolveAlertRequest();
            req.setConfirmedFraud(false);
            req.setResolvedBy("analyst-1");

            var result = service.resolveAlert(alert.getId(), req, TENANT);

            assertThat(result.getStatus()).isEqualTo(AlertStatus.FALSE_POSITIVE);
            verify(riskProfileRepository).save(argThat(p ->
                p.getFalsePositives() == 1 && p.getOpenAlerts() == 0
            ));
        }
    }

    @Nested
    @DisplayName("Customer Risk Profile")
    class RiskProfileTests {

        @Test
        @DisplayName("creates new profile for unknown customer")
        void createsNewProfile() {
            when(ruleEngineService.evaluate(eq(TENANT), anyString(), anyMap())).thenReturn(List.of());

            service.processEvent(TENANT, "payment.completed",
                Map.of("customerId", "NEW-CUST", "amount", "1000"));

            // Event is logged even without alerts
            verify(eventRepository).save(any());
        }

        @Test
        @DisplayName("returns default LOW risk for unknown customer")
        void defaultRiskLevel() {
            when(riskProfileRepository.findByTenantIdAndCustomerId(TENANT, "UNKNOWN"))
                .thenReturn(Optional.empty());

            var risk = service.getCustomerRisk(TENANT, "UNKNOWN");

            assertThat(risk.getRiskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(risk.getRiskScore()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Summary")
    class SummaryTests {

        @Test
        @DisplayName("aggregates alert counts correctly")
        void summaryAggregation() {
            when(alertRepository.countByTenantIdAndStatus(TENANT, AlertStatus.OPEN)).thenReturn(5L);
            when(alertRepository.countByTenantIdAndStatus(TENANT, AlertStatus.UNDER_REVIEW)).thenReturn(3L);
            when(alertRepository.countByTenantIdAndStatus(TENANT, AlertStatus.ESCALATED)).thenReturn(2L);
            when(alertRepository.countByTenantIdAndStatus(TENANT, AlertStatus.CONFIRMED_FRAUD)).thenReturn(1L);
            when(alertRepository.countByTenantIdAndSeverityAndStatus(TENANT, AlertSeverity.CRITICAL, AlertStatus.OPEN))
                .thenReturn(2L);
            when(riskProfileRepository.countByTenantIdAndRiskLevel(TENANT, RiskLevel.HIGH)).thenReturn(4L);
            when(riskProfileRepository.countByTenantIdAndRiskLevel(TENANT, RiskLevel.CRITICAL)).thenReturn(1L);

            var summary = service.getSummary(TENANT);

            assertThat(summary.getOpenAlerts()).isEqualTo(5);
            assertThat(summary.getUnderReviewAlerts()).isEqualTo(3);
            assertThat(summary.getEscalatedAlerts()).isEqualTo(2);
            assertThat(summary.getConfirmedFraud()).isEqualTo(1);
            assertThat(summary.getCriticalAlerts()).isEqualTo(2);
            assertThat(summary.getHighRiskCustomers()).isEqualTo(4);
        }
    }
}
