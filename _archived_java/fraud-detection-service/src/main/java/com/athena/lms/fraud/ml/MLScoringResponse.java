package com.athena.lms.fraud.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLScoringResponse {

    @JsonProperty("score")
    private double score;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("model_available")
    private boolean modelAvailable;

    @JsonProperty("details")
    private Map<String, Object> details;

    @JsonProperty("latency_ms")
    private double latencyMs;
}
