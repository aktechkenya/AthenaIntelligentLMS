package com.athena.lms.overdraft.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScoringClient {

    private final RestTemplate restTemplate;

    @Value("${athena.scoring.url}")
    private String scoringBaseUrl;

    /**
     * Fetches the latest credit score for a customer.
     * Falls back to a deterministic mock if AI scoring service is unavailable.
     */
    @SuppressWarnings("unchecked")
    public CreditScoreResult getLatestScore(String customerId) {
        try {
            String url = scoringBaseUrl + "/api/v1/scoring/customers/" + customerId + "/latest";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                Object scoreObj = response.get("finalScore");
                Object bandObj = response.get("scoreBand");
                if (scoreObj != null && bandObj != null) {
                    int score = ((Number) scoreObj).intValue();
                    String band = bandObj.toString();
                    log.info("Got credit score for customer {}: score={} band={}", customerId, score, band);
                    return new CreditScoreResult(score, band);
                }
            }
        } catch (Exception e) {
            log.warn("AI scoring unavailable for customer {}: {} — using mock", customerId, e.getMessage());
        }
        return generateMockScore(customerId);
    }

    private CreditScoreResult generateMockScore(String customerId) {
        long seed = Math.abs(customerId.hashCode());
        int baseScore = (int) (500 + (seed % 350));
        int finalScore = Math.max(300, Math.min(900, baseScore));
        String band;
        if (finalScore >= 750) band = "A";
        else if (finalScore >= 650) band = "B";
        else if (finalScore >= 550) band = "C";
        else band = "D";
        log.info("Mock score for customer {}: score={} band={}", customerId, finalScore, band);
        return new CreditScoreResult(finalScore, band);
    }

    public record CreditScoreResult(int score, String band) {}
}
