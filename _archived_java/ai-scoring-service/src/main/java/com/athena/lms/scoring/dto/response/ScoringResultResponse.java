package com.athena.lms.scoring.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ScoringResultResponse {
    private UUID id;
    private UUID requestId;
    private UUID loanApplicationId;
    private Long customerId;
    private BigDecimal baseScore;
    private BigDecimal crbContribution;
    private BigDecimal llmAdjustment;
    private BigDecimal pdProbability;
    private BigDecimal finalScore;
    private String scoreBand;
    private List<String> reasoning;
    private String llmProvider;
    private String llmModel;
    private Instant scoredAt;
    private Instant createdAt;
}
