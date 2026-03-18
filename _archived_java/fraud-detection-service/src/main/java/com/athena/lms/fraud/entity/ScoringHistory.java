package com.athena.lms.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scoring_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "ml_score", nullable = false)
    private double mlScore;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "model_available")
    @Builder.Default
    private boolean modelAvailable = true;

    @Column(name = "latency_ms")
    private double latencyMs;

    @Column(name = "rule_score")
    private double ruleScore;

    @Column(name = "anomaly_score")
    private double anomalyScore;

    @Column(name = "lgbm_score")
    private double lgbmScore;

    @Column(name = "model_details", columnDefinition = "TEXT")
    private String modelDetails;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
