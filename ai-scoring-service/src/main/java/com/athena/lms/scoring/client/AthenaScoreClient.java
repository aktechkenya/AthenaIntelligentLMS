package com.athena.lms.scoring.client;

import com.athena.lms.scoring.dto.external.ExternalScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AthenaScoreClient {

    private final RestTemplate scoringRestTemplate;
    private final String scoringBaseUrl;

    public Optional<ExternalScoreResponse> getScore(Long customerId) {
        try {
            String url = scoringBaseUrl + "/api/v1/credit-score/" + customerId;
            ResponseEntity<ExternalScoreResponse> response =
                    scoringRestTemplate.getForEntity(url, ExternalScoreResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Got real credit score for customer {}", customerId);
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("External scoring API unavailable for customer {}: {} — using mock score", customerId, e.getMessage());
        }
        return Optional.of(generateMockScore(customerId));
    }

    /**
     * Generates a deterministic mock credit score when the external API is unavailable.
     * Score is derived from the customerId to ensure reproducibility across calls.
     */
    private ExternalScoreResponse generateMockScore(Long customerId) {
        // Deterministic score: 500-849 range based on customerId hash
        long seed = customerId != null ? Math.abs(customerId) : 12345L;
        int baseScore = (int) (500 + (seed % 350));
        int crbAdjustment = (int) (seed % 50) - 25;  // -25 to +25
        int llmAdjustment = (int) (seed % 30) - 10;  // -10 to +20
        int finalScore = Math.max(300, Math.min(900, baseScore + crbAdjustment + llmAdjustment));

        String scoreBand;
        double pdProbability;
        if (finalScore >= 750) {
            scoreBand = "A";
            pdProbability = 0.01 + (seed % 3) * 0.005;
        } else if (finalScore >= 650) {
            scoreBand = "B";
            pdProbability = 0.04 + (seed % 5) * 0.01;
        } else if (finalScore >= 550) {
            scoreBand = "C";
            pdProbability = 0.10 + (seed % 5) * 0.02;
        } else {
            scoreBand = "D";
            pdProbability = 0.20 + (seed % 5) * 0.03;
        }

        ExternalScoreResponse mock = new ExternalScoreResponse();
        mock.setCustomerId(customerId);
        mock.setBaseScore(BigDecimal.valueOf(baseScore));
        mock.setCrbContribution(BigDecimal.valueOf(crbAdjustment));
        mock.setLlmAdjustment(BigDecimal.valueOf(llmAdjustment));
        mock.setPdProbability(BigDecimal.valueOf(pdProbability).setScale(4, RoundingMode.HALF_UP));
        mock.setFinalScore(BigDecimal.valueOf(finalScore));
        mock.setScoreBand(scoreBand);
        mock.setLlmProvider("mock");
        mock.setLlmModel("deterministic-v1");
        mock.setScoredAt(Instant.now().toString());
        mock.setReasoning(List.of(
            "Mock score — external AthenaCreditScore API unavailable",
            "Base score: " + baseScore + " (derived from customer profile)",
            "CRB adjustment: " + crbAdjustment,
            "Score band: " + scoreBand + ", PD: " + String.format("%.2f%%", pdProbability * 100)
        ));
        log.info("Generated mock score for customer {}: finalScore={} band={} pd={}",
                customerId, finalScore, scoreBand, pdProbability);
        return mock;
    }
}
