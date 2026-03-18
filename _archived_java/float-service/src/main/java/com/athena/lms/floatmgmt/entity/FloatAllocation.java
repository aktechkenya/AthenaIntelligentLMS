package com.athena.lms.floatmgmt.entity;

import com.athena.lms.floatmgmt.enums.FloatAllocationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "float_allocations")
@Getter
@Setter
@NoArgsConstructor
public class FloatAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "float_account_id", nullable = false)
    private UUID floatAccountId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "repaid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal repaidAmount = BigDecimal.ZERO;

    // Generated column — read-only from DB; computed in getter
    @Column(name = "outstanding", insertable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal outstandingDb;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FloatAllocationStatus status = FloatAllocationStatus.ACTIVE;

    @Column(name = "disbursed_at", nullable = false)
    private OffsetDateTime disbursedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        if (disbursedAt == null) {
            disbursedAt = now;
        }
    }

    public BigDecimal getOutstanding() {
        return allocatedAmount.subtract(repaidAmount);
    }
}
