package com.athena.lms.fraud.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Client for the fraud-ml-service Python sidecar (FastAPI).
 * Calls the combined scoring endpoint to get ML-based risk scores.
 * Falls back gracefully if the ML service is unavailable.
 */
@Service
@Slf4j
public class MLScoringClient {

    private final String mlServiceUrl;
    private final RestTemplate restTemplate;

    public MLScoringClient(
            @Value("${fraud.ml.service.url:http://fraud-ml-service:8000}") String mlServiceUrl,
            RestTemplateBuilder restTemplateBuilder) {
        this.mlServiceUrl = mlServiceUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Call the combined scoring endpoint of fraud-ml-service.
     * Returns a fallback response if the ML service is unavailable.
     */
    public MLScoringResponse scoreCombined(String tenantId, String customerId,
                                           String eventType, BigDecimal amount,
                                           double ruleScore) {
        try {
            String url = mlServiceUrl + "/api/v1/score/combined";

            MLScoringRequest request = MLScoringRequest.builder()
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .eventType(eventType)
                    .amount(amount)
                    .ruleScore(ruleScore)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<MLScoringResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    MLScoringResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("ML scoring result: score={} risk={} latency={}ms",
                        response.getBody().getScore(),
                        response.getBody().getRiskLevel(),
                        response.getBody().getLatencyMs());
                return response.getBody();
            }

            log.warn("ML scoring returned non-2xx status: {}", response.getStatusCode());
            return buildFallbackResponse(ruleScore);
        } catch (Exception e) {
            log.debug("ML scoring unavailable (fallback to rules): {}", e.getMessage());
            return buildFallbackResponse(ruleScore);
        }
    }

    /**
     * Check if the fraud-ml-service is healthy.
     */
    public boolean checkHealth() {
        try {
            String url = mlServiceUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Trigger model retraining (anomaly or fraud-scorer).
     */
    public Map<String, Object> triggerTraining(String modelType) {
        try {
            String url = mlServiceUrl + "/api/v1/train/" + modelType;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Triggered ML training for model: {}", modelType);
                @SuppressWarnings("unchecked")
                Map<String, Object> body = response.getBody();
                return body;
            }
            return Map.of("status", "error", "message", "Non-2xx response: " + response.getStatusCode());
        } catch (Exception e) {
            log.warn("Failed to trigger ML training for {}: {}", modelType, e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * Get training status from the ML service.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTrainingStatus() {
        try {
            String url = mlServiceUrl + "/api/v1/train/status";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("status", "unknown");
        } catch (Exception e) {
            log.debug("Failed to get ML training status: {}", e.getMessage());
            return Map.of("status", "unavailable", "message", e.getMessage());
        }
    }

    private MLScoringResponse buildFallbackResponse(double ruleScore) {
        String riskLevel;
        if (ruleScore >= 0.8) riskLevel = "CRITICAL";
        else if (ruleScore >= 0.6) riskLevel = "HIGH";
        else if (ruleScore >= 0.3) riskLevel = "MEDIUM";
        else riskLevel = "LOW";

        return MLScoringResponse.builder()
                .score(ruleScore)
                .riskLevel(riskLevel)
                .modelAvailable(false)
                .details(Map.of("fallback", true, "rule_score", ruleScore))
                .latencyMs(0.0)
                .build();
    }
}
