package com.athena.lms.origination.entity;

import com.athena.lms.origination.enums.ApplicationStatus;
import com.athena.lms.origination.enums.RiskGrade;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 18, scale = 2)
    private BigDecimal approvedAmount;

    @Builder.Default
    @Column(name = "currency", length = 3)
    private String currency = "KES";

    @Column(name = "tenor_months", nullable = false)
    private Integer tenorMonths;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade", length = 5)
    private RiskGrade riskGrade;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "interest_rate", precision = 8, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "disbursed_amount", precision = 18, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @Column(name = "disbursement_account", length = 100)
    private String disbursementAccount;

    @Column(name = "reviewer_id", length = 100)
    private String reviewerId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ApplicationCollateral> collaterals = new HashSet<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ApplicationNote> notes = new HashSet<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();
}
