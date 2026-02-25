package com.athena.lms.payment.entity;

import com.athena.lms.payment.enums.PaymentChannel;
import com.athena.lms.payment.enums.PaymentStatus;
import com.athena.lms.payment.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 50)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_channel", nullable = false, length = 50)
    private PaymentChannel paymentChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "KES";

    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @Column(name = "internal_reference", length = 100, unique = true)
    private String internalReference;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "reversal_reason", columnDefinition = "TEXT")
    private String reversalReason;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "initiated_at")
    private OffsetDateTime initiatedAt = OffsetDateTime.now();

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

        @CreationTimestamp
@Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
