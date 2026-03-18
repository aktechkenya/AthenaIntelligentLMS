package com.athena.lms.reporting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "portfolio_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "period", nullable = false, length = 20)
    @Builder.Default
    private String period = "DAILY";

    @Column(name = "total_loans", nullable = false)
    @Builder.Default
    private Integer totalLoans = 0;

    @Column(name = "active_loans", nullable = false)
    @Builder.Default
    private Integer activeLoans = 0;

    @Column(name = "closed_loans", nullable = false)
    @Builder.Default
    private Integer closedLoans = 0;

    @Column(name = "defaulted_loans", nullable = false)
    @Builder.Default
    private Integer defaultedLoans = 0;

    @Column(name = "total_disbursed", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalDisbursed = BigDecimal.ZERO;

    @Column(name = "total_outstanding", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    @Column(name = "total_collected", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalCollected = BigDecimal.ZERO;

    @Column(name = "watch_loans", nullable = false)
    @Builder.Default
    private Integer watchLoans = 0;

    @Column(name = "substandard_loans", nullable = false)
    @Builder.Default
    private Integer substandardLoans = 0;

    @Column(name = "doubtful_loans", nullable = false)
    @Builder.Default
    private Integer doubtfulLoans = 0;

    @Column(name = "loss_loans", nullable = false)
    @Builder.Default
    private Integer lossLoans = 0;

    @Column(name = "par30", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal par30 = BigDecimal.ZERO;

    @Column(name = "par90", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal par90 = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (period == null) period = "DAILY";
    }
}
