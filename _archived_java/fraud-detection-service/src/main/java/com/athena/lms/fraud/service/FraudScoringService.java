package com.athena.lms.fraud.service;

import com.athena.lms.fraud.entity.ScoringHistory;
import com.athena.lms.fraud.ml.MLScoringClient;
import com.athena.lms.fraud.ml.MLScoringResponse;
import com.athena.lms.fraud.repository.ScoringHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FraudScoringService {

    private final MLScoringClient mlScoringClient;
    private final ScoringHistoryRepository scoringHistoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Score a transaction via the ML service and persist the result.
     */
    public MLScoringResponse scoreTransaction(String tenantId, String customerId,
                                               String eventType, BigDecimal amount,
                                               double ruleScore) {
        MLScoringResponse response = mlScoringClient.scoreCombined(
                tenantId, customerId, eventType, amount, ruleScore);

        // Extract sub-scores from details
        double anomalyScore = extractSubScore(response.getDetails(), "anomaly", "anomaly_score");
        double lgbmScore = extractSubScore(response.getDetails(), "lgbm", "fraud_probability");

        // Serialize details to JSON
        String modelDetailsJson = null;
        try {
            if (response.getDetails() != null) {
                modelDetailsJson = objectMapper.writeValueAsString(response.getDetails());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize ML scoring details: {}", e.getMessage());
        }

        // Persist scoring history
        ScoringHistory history = ScoringHistory.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .eventType(eventType)
                .amount(amount)
                .mlScore(response.getScore())
                .riskLevel(response.getRiskLevel())
                .modelAvailable(response.isModelAvailable())
                .latencyMs(response.getLatencyMs())
                .ruleScore(ruleScore)
                .anomalyScore(anomalyScore)
                .lgbmScore(lgbmScore)
                .modelDetails(modelDetailsJson)
                .build();

        scoringHistoryRepository.save(history);

        log.info("Scored transaction: tenant={} customer={} event={} score={} risk={}",
                tenantId, customerId, eventType, response.getScore(), response.getRiskLevel());

        return response;
    }

    /**
     * Get customer scoring history, paginated.
     */
    @Transactional(readOnly = true)
    public Page<ScoringHistory> getCustomerScoringHistory(String tenantId, String customerId,
                                                           int page, int size) {
        return scoringHistoryRepository.findByTenantIdAndCustomerId(
                tenantId, customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * Get scoring dashboard stats for a tenant.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getScoringStats(String tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Counts by risk level
        Map<String, Long> riskCounts = new LinkedHashMap<>();
        for (String level : List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")) {
            riskCounts.put(level, scoringHistoryRepository.countByTenantIdAndRiskLevel(tenantId, level));
        }
        stats.put("countsByRiskLevel", riskCounts);

        // Average scores by risk level
        Map<String, Double> avgScores = new LinkedHashMap<>();
        List<Object[]> avgRows = scoringHistoryRepository.averageScoreByRiskLevel(tenantId);
        for (Object[] row : avgRows) {
            avgScores.put((String) row[0], ((Number) row[1]).doubleValue());
        }
        stats.put("averageScoresByRiskLevel", avgScores);

        // Volume per day (last 30 days)
        List<Map<String, Object>> volumePerDay = new ArrayList<>();
        List<Object[]> volumeRows = scoringHistoryRepository.scoringVolumePerDay(tenantId, 30);
        for (Object[] row : volumeRows) {
            Map<String, Object> dayEntry = new LinkedHashMap<>();
            dayEntry.put("date", row[0].toString());
            dayEntry.put("volume", ((Number) row[1]).longValue());
            volumePerDay.add(dayEntry);
        }
        stats.put("volumePerDay", volumePerDay);

        return stats;
    }

    @SuppressWarnings("unchecked")
    private double extractSubScore(Map<String, Object> details, String section, String key) {
        if (details == null) return 0.0;
        Object sectionObj = details.get(section);
        if (sectionObj instanceof Map) {
            Object value = ((Map<String, Object>) sectionObj).get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return 0.0;
    }
}
