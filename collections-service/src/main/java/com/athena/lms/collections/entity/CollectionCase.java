package com.athena.lms.collections.entity;

import com.athena.lms.collections.enums.CasePriority;
import com.athena.lms.collections.enums.CaseStatus;
import com.athena.lms.collections.enums.CollectionStage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "case_number", nullable = false, length = 50)
    private String caseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private CasePriority priority;

    @Column(name = "current_dpd", nullable = false)
    private int currentDpd;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 30)
    private CollectionStage currentStage;

    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "last_action_at")
    private OffsetDateTime lastActionAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (openedAt == null) openedAt = now;
        if (status == null) status = CaseStatus.OPEN;
        if (priority == null) priority = CasePriority.NORMAL;
        if (currentStage == null) currentStage = CollectionStage.WATCH;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
