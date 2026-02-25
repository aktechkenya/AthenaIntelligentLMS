package com.athena.lms.floatmgmt.entity;

import com.athena.lms.floatmgmt.enums.FloatTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "float_transactions")
@Getter
@Setter
@NoArgsConstructor
public class FloatTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "float_account_id", nullable = false)
    private UUID floatAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private FloatTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "narration", columnDefinition = "TEXT")
    private String narration;

    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
