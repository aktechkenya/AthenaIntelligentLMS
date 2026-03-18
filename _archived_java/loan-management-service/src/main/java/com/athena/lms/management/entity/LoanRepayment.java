package com.athena.lms.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_repayments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "KES";

    @Column(name = "penalty_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal penaltyApplied = BigDecimal.ZERO;

    @Column(name = "fee_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal feeApplied = BigDecimal.ZERO;

    @Column(name = "interest_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal interestApplied = BigDecimal.ZERO;

    @Column(name = "principal_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalApplied = BigDecimal.ZERO;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
