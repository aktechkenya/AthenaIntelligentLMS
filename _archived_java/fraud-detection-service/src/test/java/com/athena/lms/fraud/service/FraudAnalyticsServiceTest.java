package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.response.FraudAnalyticsResponse;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import com.athena.lms.fraud.repository.FraudCaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudAnalyticsServiceTest {

    @Mock private FraudAlertRepository alertRepository;
    @Mock private FraudCaseRepository caseRepository;

    @InjectMocks private FraudAnalyticsService service;

    private static final String TENANT = "test-tenant";

    @Test
    @DisplayName("computes resolution rate from total and resolved counts")
    void resolutionRate() {
        when(alertRepository.countByTenantId(TENANT)).thenReturn(100L);
        when(alertRepository.countResolved(TENANT)).thenReturn(40L);
        when(caseRepository.countActiveCases(TENANT)).thenReturn(5L);
        when(alertRepository.countByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countConfirmedByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countFalsePositiveByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countByDay(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of());
        when(alertRepository.countByAlertType(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of());

        FraudAnalyticsResponse result = service.getAnalytics(TENANT, 30);

        assertThat(result.getTotalAlerts()).isEqualTo(100);
        assertThat(result.getResolvedAlerts()).isEqualTo(40);
        assertThat(result.getResolutionRate()).isEqualTo(0.4);
        assertThat(result.getActiveCases()).isEqualTo(5);
    }

    @Test
    @DisplayName("computes rule effectiveness with precision rates")
    void ruleEffectiveness() {
        when(alertRepository.countByTenantId(TENANT)).thenReturn(50L);
        when(alertRepository.countResolved(TENANT)).thenReturn(20L);
        when(caseRepository.countActiveCases(TENANT)).thenReturn(2L);
        when(alertRepository.countByRule(TENANT)).thenReturn(List.of(
                new Object[]{"LARGE_TXN", 30L},
                new Object[]{"STRUCTURING", 20L}
        ));
        when(alertRepository.countConfirmedByRule(TENANT)).thenReturn(List.of(
                new Object[]{"LARGE_TXN", 10L},
                new Object[]{"STRUCTURING", 8L}
        ));
        when(alertRepository.countFalsePositiveByRule(TENANT)).thenReturn(List.of(
                new Object[]{"LARGE_TXN", 5L},
                new Object[]{"STRUCTURING", 2L}
        ));
        when(alertRepository.countByDay(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of());
        when(alertRepository.countByAlertType(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of());

        FraudAnalyticsResponse result = service.getAnalytics(TENANT, 30);

        assertThat(result.getRuleEffectiveness()).hasSize(2);
        FraudAnalyticsResponse.RuleEffectiveness largeTxn = result.getRuleEffectiveness().get(0);
        assertThat(largeTxn.getRuleCode()).isEqualTo("LARGE_TXN");
        assertThat(largeTxn.getTotalTriggers()).isEqualTo(30);
        assertThat(largeTxn.getConfirmedFraud()).isEqualTo(10);
        assertThat(largeTxn.getFalsePositives()).isEqualTo(5);
        // precision = 10 / (10+5) = 0.6667
        assertThat(largeTxn.getPrecisionRate()).isCloseTo(0.6667, org.assertj.core.data.Offset.offset(0.001));

        assertThat(result.getConfirmedFraudCount()).isEqualTo(18);
        assertThat(result.getFalsePositiveCount()).isEqualTo(7);
        // overall precision = 18 / (18+7) = 0.72
        assertThat(result.getPrecisionRate()).isCloseTo(0.72, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("handles zero total alerts without division by zero")
    void zeroDivision() {
        when(alertRepository.countByTenantId(TENANT)).thenReturn(0L);
        when(alertRepository.countResolved(TENANT)).thenReturn(0L);
        when(caseRepository.countActiveCases(TENANT)).thenReturn(0L);
        when(alertRepository.countByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countConfirmedByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countFalsePositiveByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countByDay(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of());
        when(alertRepository.countByAlertType(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of());

        FraudAnalyticsResponse result = service.getAnalytics(TENANT, 30);

        assertThat(result.getResolutionRate()).isEqualTo(0.0);
        assertThat(result.getPrecisionRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("includes daily trend and type distribution data")
    void dailyTrendAndTypes() {
        when(alertRepository.countByTenantId(TENANT)).thenReturn(10L);
        when(alertRepository.countResolved(TENANT)).thenReturn(5L);
        when(caseRepository.countActiveCases(TENANT)).thenReturn(1L);
        when(alertRepository.countByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countConfirmedByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countFalsePositiveByRule(TENANT)).thenReturn(List.of());
        when(alertRepository.countByDay(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of(
                new Object[]{"2026-03-01", 3L},
                new Object[]{"2026-03-02", 7L}
        ));
        when(alertRepository.countByAlertType(eq(TENANT), any(OffsetDateTime.class))).thenReturn(List.of(
                new Object[]{"LARGE_TRANSACTION", 6L},
                new Object[]{"STRUCTURING", 4L}
        ));

        FraudAnalyticsResponse result = service.getAnalytics(TENANT, 30);

        assertThat(result.getDailyTrend()).hasSize(2);
        assertThat(result.getDailyTrend().get(0).getDate()).isEqualTo("2026-03-01");
        assertThat(result.getDailyTrend().get(0).getCount()).isEqualTo(3);

        assertThat(result.getAlertsByType()).hasSize(2);
        assertThat(result.getAlertsByType().get(0).getType()).isEqualTo("LARGE_TRANSACTION");
    }
}
