package com.athena.lms.fraud.entity;

import com.athena.lms.fraud.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "customer_risk_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "risk_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal riskScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "total_alerts")
    @Builder.Default
    private Integer totalAlerts = 0;

    @Column(name = "open_alerts")
    @Builder.Default
    private Integer openAlerts = 0;

    @Column(name = "confirmed_fraud")
    @Builder.Default
    private Integer confirmedFraud = 0;

    @Column(name = "false_positives")
    @Builder.Default
    private Integer falsePositives = 0;

    @Column(name = "avg_transaction_amount", precision = 19, scale = 4)
    private BigDecimal avgTransactionAmount;

    @Column(name = "transaction_count_30d")
    @Builder.Default
    private Integer transactionCount30d = 0;

    @Column(name = "last_alert_at")
    private OffsetDateTime lastAlertAt;

    @Column(name = "last_scored_at")
    private OffsetDateTime lastScoredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> factors = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
