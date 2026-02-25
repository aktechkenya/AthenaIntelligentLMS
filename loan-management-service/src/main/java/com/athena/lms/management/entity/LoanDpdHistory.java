package com.athena.lms.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_dpd_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanDpdHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "dpd", nullable = false)
    private Integer dpd;

    @Column(name = "stage", nullable = false, length = 30)
    private String stage;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate = LocalDate.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
