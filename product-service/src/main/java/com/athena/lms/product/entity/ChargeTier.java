package com.athena.lms.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "charge_tiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TransactionCharge charge;

    @Column(name = "from_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal toAmount;

    @Column(name = "flat_amount", precision = 15, scale = 2)
    private BigDecimal flatAmount;

    @Column(name = "percentage_rate", precision = 10, scale = 6)
    private BigDecimal percentageRate;
}
