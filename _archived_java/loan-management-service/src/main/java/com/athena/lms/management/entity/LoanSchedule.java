package com.athena.lms.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalDue = BigDecimal.ZERO;

    @Column(name = "interest_due", nullable = false, precision = 18, scale = 2)
    private BigDecimal interestDue = BigDecimal.ZERO;

    @Column(name = "fee_due", nullable = false, precision = 18, scale = 2)
    private BigDecimal feeDue = BigDecimal.ZERO;

    @Column(name = "penalty_due", nullable = false, precision = 18, scale = 2)
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "total_due", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDue = BigDecimal.ZERO;

    @Column(name = "principal_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "fee_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal feePaid = BigDecimal.ZERO;

    @Column(name = "penalty_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Column(name = "total_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "paid_date")
    private LocalDate paidDate;
}
