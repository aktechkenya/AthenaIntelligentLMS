package com.athena.lms.management.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class RepaymentResponse {
    private UUID id;
    private String status;
    private BigDecimal amount;
    private String currency;
    private BigDecimal penaltyApplied;
    private BigDecimal feeApplied;
    private BigDecimal interestApplied;
    private BigDecimal principalApplied;
    private String paymentReference;
    private String paymentMethod;
    private LocalDate paymentDate;
    private OffsetDateTime createdAt;
}
