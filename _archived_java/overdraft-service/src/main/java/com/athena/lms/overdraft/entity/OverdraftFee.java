package com.athena.lms.overdraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "overdraft_fees")
@Getter
@Setter
@NoArgsConstructor
public class OverdraftFee {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "fee_type", nullable = false, length = 30)
    private String feeType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "charged_at")
    private OffsetDateTime chargedAt;

    @Column(name = "waived_at")
    private OffsetDateTime waivedAt;

    @Column(name = "waived_by", length = 200)
    private String waivedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
