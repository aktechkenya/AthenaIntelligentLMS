package com.athena.lms.scoring.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalScoreResponse {

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("base_score")
    private BigDecimal baseScore;

    @JsonProperty("crb_contribution")
    private BigDecimal crbContribution;

    @JsonProperty("llm_adjustment")
    private BigDecimal llmAdjustment;

    @JsonProperty("pd_probability")
    private BigDecimal pdProbability;

    @JsonProperty("final_score")
    private BigDecimal finalScore;

    @JsonProperty("score_band")
    private String scoreBand;

    @JsonProperty("reasoning")
    private List<String> reasoning;

    @JsonProperty("llm_provider")
    private String llmProvider;

    @JsonProperty("llm_model")
    private String llmModel;

    @JsonProperty("scored_at")
    private String scoredAt;
}
