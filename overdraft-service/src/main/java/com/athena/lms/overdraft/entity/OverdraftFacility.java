package com.athena.lms.overdraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "overdraft_facilities")
@Getter
@Setter
@NoArgsConstructor
public class OverdraftFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore;

    @Column(name = "credit_band", nullable = false, length = 1)
    private String creditBand;

    @Column(name = "approved_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal approvedLimit;

    @Column(name = "drawn_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal drawnAmount = BigDecimal.ZERO;

    @Column(name = "drawn_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal drawnPrincipal = BigDecimal.ZERO;

    @Column(name = "accrued_interest", nullable = false, precision = 19, scale = 4)
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "dpd", nullable = false)
    private Integer dpd = 0;

    @Column(name = "npl_stage", nullable = false, length = 20)
    private String nplStage = "PERFORMING";

    @Column(name = "last_billing_date")
    private LocalDate lastBillingDate;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "last_dpd_refresh")
    private LocalDate lastDpdRefresh;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        appliedAt = now;
        approvedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Recalculates drawnAmount from principal + interest components.
     * Call after modifying drawnPrincipal or accruedInterest.
     */
    public void recalculateDrawnAmount() {
        this.drawnAmount = this.drawnPrincipal.add(this.accruedInterest);
    }
}
