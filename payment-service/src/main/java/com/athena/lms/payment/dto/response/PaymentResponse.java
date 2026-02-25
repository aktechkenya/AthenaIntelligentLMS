package com.athena.lms.payment.dto.response;

import com.athena.lms.payment.enums.PaymentChannel;
import com.athena.lms.payment.enums.PaymentStatus;
import com.athena.lms.payment.enums.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class PaymentResponse {
    private UUID id;
    private String tenantId;
    private String customerId;
    private UUID loanId;
    private UUID applicationId;
    private PaymentType paymentType;
    private PaymentChannel paymentChannel;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String externalReference;
    private String internalReference;
    private String description;
    private String failureReason;
    private String reversalReason;
    private OffsetDateTime initiatedAt;
    private OffsetDateTime processedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime reversedAt;
    private OffsetDateTime createdAt;
}
