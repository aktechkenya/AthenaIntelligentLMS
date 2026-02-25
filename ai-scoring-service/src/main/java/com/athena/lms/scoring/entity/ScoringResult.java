package com.athena.lms.scoring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scoring_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "base_score", precision = 8, scale = 2)
    private BigDecimal baseScore;

    @Column(name = "crb_contribution", precision = 8, scale = 2)
    private BigDecimal crbContribution;

    @Column(name = "llm_adjustment", precision = 8, scale = 2)
    private BigDecimal llmAdjustment;

    @Column(name = "pd_probability", precision = 8, scale = 6)
    private BigDecimal pdProbability;

    @Column(name = "final_score", precision = 8, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "score_band", length = 50)
    private String scoreBand;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "llm_provider", length = 50)
    private String llmProvider;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "scored_at")
    private Instant scoredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
