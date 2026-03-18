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
@Table(name = "overdraft_interest_charges")
@Getter
@Setter
@NoArgsConstructor
public class OverdraftInterestCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

    @Column(name = "drawn_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal drawnAmount;

    @Column(name = "daily_rate", nullable = false, precision = 10, scale = 8)
    private BigDecimal dailyRate;

    @Column(name = "interest_charged", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestCharged;

    @Column(name = "reference", nullable = false, length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
