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
@Table(name = "overdraft_billing_statements")
@Getter
@Setter
@NoArgsConstructor
public class OverdraftBillingStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "billing_date", nullable = false)
    private LocalDate billingDate;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "interest_accrued", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestAccrued = BigDecimal.ZERO;

    @Column(name = "fees_charged", nullable = false, precision = 19, scale = 4)
    private BigDecimal feesCharged = BigDecimal.ZERO;

    @Column(name = "payments_received", nullable = false, precision = 19, scale = 4)
    private BigDecimal paymentsReceived = BigDecimal.ZERO;

    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Column(name = "minimum_payment_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumPaymentDue = BigDecimal.ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
