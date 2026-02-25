package com.athena.lms.management.entity;

import com.athena.lms.management.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "disbursed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(name = "outstanding_interest", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingInterest = BigDecimal.ZERO;

    @Column(name = "outstanding_fees", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingFees = BigDecimal.ZERO;

    @Column(name = "outstanding_penalty", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingPenalty = BigDecimal.ZERO;

    @Column(name = "currency", length = 3)
    private String currency = "KES";

    @Column(name = "interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "tenor_months", nullable = false)
    private Integer tenorMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 20)
    private RepaymentFrequency repaymentFrequency = RepaymentFrequency.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType = ScheduleType.EMI;

    @Column(name = "disbursed_at", nullable = false)
    private OffsetDateTime disbursedAt;

    @Column(name = "first_repayment_date", nullable = false)
    private LocalDate firstRepaymentDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    private LoanStage stage = LoanStage.PERFORMING;

    @Column(name = "dpd", nullable = false)
    private Integer dpd = 0;

    @Column(name = "last_repayment_date")
    private LocalDate lastRepaymentDate;

    @Column(name = "last_repayment_amount", precision = 18, scale = 2)
    private BigDecimal lastRepaymentAmount;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanSchedule> schedules = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanRepayment> repayments = new ArrayList<>();
}
