package com.athena.lms.fraud.service;

import com.athena.lms.fraud.ml.MLScoringClient;
import com.athena.lms.fraud.ml.MLScoringResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MLScoringClientTest {

    // Points to a non-running URL so all calls hit the fallback path
    private final MLScoringClient client = new MLScoringClient(
            "http://localhost:19999", new RestTemplateBuilder());

    @Test
    @DisplayName("returns fallback response when ML service is unreachable")
    void fallbackWhenUnreachable() {
        MLScoringResponse result = client.scoreCombined(
            "test-tenant", "CUST-1", "payment.completed",
            new BigDecimal("50000"), 0.5
        );

        assertThat(result).isNotNull();
        assertThat(result.isModelAvailable()).isFalse();
        assertThat(result.getScore()).isEqualTo(0.5);
        assertThat(result.getRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("handles null amount gracefully")
    void handlesNullAmount() {
        MLScoringResponse result = client.scoreCombined(
            "test-tenant", "CUST-1", "payment.completed",
            null, 0.0
        );

        assertThat(result).isNotNull();
        assertThat(result.isModelAvailable()).isFalse();
    }

    @Test
    @DisplayName("health check returns false when service unavailable")
    void healthCheckFalse() {
        assertThat(client.checkHealth()).isFalse();
    }
}
