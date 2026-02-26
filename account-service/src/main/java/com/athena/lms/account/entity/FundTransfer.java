package com.athena.lms.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fund_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 20)
    private TransferType transferType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "reference", nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "narration", length = 255)
    private String narration;

    @Column(name = "charge_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal chargeAmount = BigDecimal.ZERO;

    @Column(name = "charge_reference", length = 100)
    private String chargeReference;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @CreationTimestamp
    @Column(name = "initiated_at", nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    public enum TransferType { INTERNAL, THIRD_PARTY, WALLET }
    public enum TransferStatus { PENDING, PROCESSING, COMPLETED, FAILED, REVERSED }
}
