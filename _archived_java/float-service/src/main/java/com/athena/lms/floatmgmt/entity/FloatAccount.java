package com.athena.lms.floatmgmt.entity;

import com.athena.lms.floatmgmt.enums.FloatAccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "float_accounts")
@Getter
@Setter
@NoArgsConstructor
public class FloatAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "account_name", nullable = false, length = 200)
    private String accountName;

    @Column(name = "account_code", nullable = false, length = 50)
    private String accountCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Column(name = "float_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal floatLimit = BigDecimal.ZERO;

    @Column(name = "drawn_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal drawnAmount = BigDecimal.ZERO;

    // Generated column — read-only from DB; computed in getter
    @Column(name = "available", insertable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal availableDb;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FloatAccountStatus status = FloatAccountStatus.ACTIVE;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public BigDecimal getAvailable() {
        return floatLimit.subtract(drawnAmount);
    }
}
