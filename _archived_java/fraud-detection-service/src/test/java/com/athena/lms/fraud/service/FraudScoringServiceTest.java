package com.athena.lms.fraud.service;

import com.athena.lms.fraud.entity.ScoringHistory;
import com.athena.lms.fraud.ml.MLScoringClient;
import com.athena.lms.fraud.ml.MLScoringResponse;
import com.athena.lms.fraud.repository.ScoringHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudScoringServiceTest {

    @Mock
    MLScoringClient mlScoringClient;

    @Mock
    ScoringHistoryRepository scoringHistoryRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    FraudScoringService fraudScoringService;

    @Test
    @DisplayName("scoreTransaction persists history and returns response")
    void scoreTransactionPersists() {
        MLScoringResponse mlResponse = MLScoringResponse.builder()
                .score(0.72)
                .riskLevel("HIGH")
                .modelAvailable(true)
                .latencyMs(45.0)
                .details(Map.of(
                        "anomaly", Map.of("anomaly_score", 0.65),
                        "lgbm", Map.of("fraud_probability", 0.78)
                ))
                .build();

        when(mlScoringClient.scoreCombined(any(), any(), any(), any(), anyDouble()))
                .thenReturn(mlResponse);
        when(scoringHistoryRepository.save(any(ScoringHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MLScoringResponse result = fraudScoringService.scoreTransaction(
                "t1", "CUST-1", "payment.completed", new BigDecimal("50000"), 0.7);

        assertThat(result.getScore()).isEqualTo(0.72);
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");

        verify(scoringHistoryRepository).save(argThat(h ->
                h.getMlScore() == 0.72
                && h.getRiskLevel().equals("HIGH")
                && h.getCustomerId().equals("CUST-1")
                && h.getAnomalyScore() == 0.65
                && h.getLgbmScore() == 0.78
        ));
    }

    @Test
    @DisplayName("scoreTransaction extracts sub-scores from nested details")
    void extractsSubScores() {
        MLScoringResponse mlResponse = MLScoringResponse.builder()
                .score(0.3)
                .riskLevel("MEDIUM")
                .modelAvailable(true)
                .latencyMs(20.0)
                .details(Map.of(
                        "anomaly", Map.of("anomaly_score", 0.25, "is_anomaly", false),
                        "lgbm", Map.of("fraud_probability", 0.35, "model_alias", "champion")
                ))
                .build();

        when(mlScoringClient.scoreCombined(any(), any(), any(), any(), anyDouble()))
                .thenReturn(mlResponse);
        when(scoringHistoryRepository.save(any(ScoringHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        fraudScoringService.scoreTransaction("t1", "CUST-2", "transfer.completed", null, 0.0);

        verify(scoringHistoryRepository).save(argThat(h ->
                h.getAnomalyScore() == 0.25 && h.getLgbmScore() == 0.35
        ));
    }

    @Test
    @DisplayName("getScoringStats returns risk level counts")
    void scoringStats() {
        when(scoringHistoryRepository.countByTenantIdAndRiskLevel("t1", "LOW")).thenReturn(10L);
        when(scoringHistoryRepository.countByTenantIdAndRiskLevel("t1", "MEDIUM")).thenReturn(5L);
        when(scoringHistoryRepository.countByTenantIdAndRiskLevel("t1", "HIGH")).thenReturn(3L);
        when(scoringHistoryRepository.countByTenantIdAndRiskLevel("t1", "CRITICAL")).thenReturn(1L);
        when(scoringHistoryRepository.averageScoreByRiskLevel("t1")).thenReturn(List.of());
        when(scoringHistoryRepository.scoringVolumePerDay(eq("t1"), anyInt())).thenReturn(List.of());

        Map<String, Object> stats = fraudScoringService.getScoringStats("t1");

        @SuppressWarnings("unchecked")
        Map<String, Long> counts = (Map<String, Long>) stats.get("countsByRiskLevel");
        assertThat(counts.get("LOW")).isEqualTo(10L);
        assertThat(counts.get("CRITICAL")).isEqualTo(1L);
    }
}
