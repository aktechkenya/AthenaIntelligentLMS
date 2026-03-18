package com.athena.lms.fraud.entity;

import com.athena.lms.fraud.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "fraud_cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCase {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "case_number", nullable = false, unique = true, length = 30)
    private String caseNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CaseStatus status = CaseStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private AlertSeverity priority = AlertSeverity.MEDIUM;

    @Column(name = "customer_id", length = 100)
    private String customerId;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "total_exposure", precision = 19, scale = 4)
    private BigDecimal totalExposure;

    @Column(name = "confirmed_loss", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal confirmedLoss = BigDecimal.ZERO;

    @ElementCollection
    @CollectionTable(name = "fraud_case_alert_ids", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "alert_id")
    private Set<UUID> alertIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "sla_deadline")
    private OffsetDateTime slaDeadline;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "outcome", length = 100)
    private String outcome;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
