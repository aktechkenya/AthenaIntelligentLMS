package com.athena.lms.product.entity;

import com.athena.lms.product.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "min_tenor_days")
    private Integer minTenorDays;

    @Column(name = "max_tenor_days")
    private Integer maxTenorDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    @Builder.Default
    private ScheduleType scheduleType = ScheduleType.EMI;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 20)
    @Builder.Default
    private RepaymentFrequency repaymentFrequency = RepaymentFrequency.MONTHLY;

    @Column(name = "nominal_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal nominalRate;

    @Column(name = "penalty_rate", precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal penaltyRate = BigDecimal.ZERO;

    @Column(name = "penalty_grace_days")
    @Builder.Default
    private int penaltyGraceDays = 1;

    @Column(name = "grace_period_days")
    @Builder.Default
    private int gracePeriodDays = 0;

    @Column(name = "processing_fee_rate", precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal processingFeeRate = BigDecimal.ZERO;

    @Column(name = "processing_fee_min", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal processingFeeMin = BigDecimal.ZERO;

    @Column(name = "processing_fee_max", precision = 15, scale = 2)
    private BigDecimal processingFeeMax;

    @Column(name = "requires_collateral")
    @Builder.Default
    private boolean requiresCollateral = false;

    @Column(name = "min_credit_score")
    @Builder.Default
    private int minCreditScore = 0;

    @Column(name = "max_dtir", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxDtir = new BigDecimal("100.00");

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "template_id", length = 50)
    private String templateId;

    @Column(name = "requires_two_person_auth")
    @Builder.Default
    private boolean requiresTwoPersonAuth = false;

    @Column(name = "auth_threshold_amount", precision = 15, scale = 2)
    private BigDecimal authThresholdAmount;

    @Column(name = "pending_authorization")
    @Builder.Default
    private boolean pendingAuthorization = false;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ProductFee> fees = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
