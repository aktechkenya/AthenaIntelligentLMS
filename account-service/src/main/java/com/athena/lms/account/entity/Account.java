package com.athena.lms.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "kyc_tier", nullable = false)
    @Builder.Default
    private int kycTier = 0;

    @Column(name = "daily_transaction_limit", precision = 15, scale = 2)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "monthly_transaction_limit", precision = 15, scale = 2)
    private BigDecimal monthlyTransactionLimit;

    @Column(name = "account_name", length = 100)
    private String accountName;

    // Balance is loaded separately via AccountBalanceRepository to avoid cascade complexity.
    // AccountBalance owns the FK (account_id -> accounts.id).
    @Transient
    private AccountBalance balance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum AccountType { CURRENT, SAVINGS, WALLET }
    public enum AccountStatus { ACTIVE, FROZEN, DORMANT, CLOSED }
}
