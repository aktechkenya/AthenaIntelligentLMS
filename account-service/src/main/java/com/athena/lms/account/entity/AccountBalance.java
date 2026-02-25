package com.athena.lms.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "available_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "ledger_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal ledgerBalance = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
