package com.athena.lms.product.entity;

import com.athena.lms.product.enums.CalculationType;
import com.athena.lms.product.enums.FeeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_fees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Product product;

    @Column(name = "fee_name", nullable = false, length = 100)
    private String feeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 20)
    private FeeType feeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 20)
    private CalculationType calculationType;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "rate", precision = 10, scale = 6)
    private BigDecimal rate;

    @Column(name = "is_mandatory")
    @Builder.Default
    private boolean isMandatory = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
