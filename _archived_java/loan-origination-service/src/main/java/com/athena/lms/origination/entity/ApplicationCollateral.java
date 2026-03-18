package com.athena.lms.origination.entity;

import com.athena.lms.origination.enums.CollateralType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "application_collaterals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationCollateral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_type", nullable = false, length = 50)
    private CollateralType collateralType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "estimated_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedValue;

    @Builder.Default
    @Column(name = "currency", length = 3)
    private String currency = "KES";

    @Column(name = "document_ref", length = 255)
    private String documentRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
