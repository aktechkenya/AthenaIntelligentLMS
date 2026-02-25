package com.athena.lms.compliance.entity;

import com.athena.lms.compliance.enums.KycStatus;
import com.athena.lms.compliance.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "check_type", nullable = false, length = 50)
    @Builder.Default
    private String checkType = "FULL_KYC";

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "checked_by", length = 100)
    private String checkedBy;

    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
