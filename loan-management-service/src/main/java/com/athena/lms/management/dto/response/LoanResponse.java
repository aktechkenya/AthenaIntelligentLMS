package com.athena.lms.management.dto.response;

import com.athena.lms.management.enums.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class LoanResponse {
    private UUID id;
    private String tenantId;
    private UUID applicationId;
    private String customerId;
    private UUID productId;
    private BigDecimal disbursedAmount;
    private BigDecimal outstandingPrincipal;
    private BigDecimal outstandingInterest;
    private BigDecimal outstandingFees;
    private BigDecimal outstandingPenalty;
    private BigDecimal totalOutstanding;
    private String currency;
    private BigDecimal interestRate;
    private Integer tenorMonths;
    private RepaymentFrequency repaymentFrequency;
    private ScheduleType scheduleType;
    private OffsetDateTime disbursedAt;
    private LocalDate firstRepaymentDate;
    private LocalDate maturityDate;
    private LoanStatus status;
    private LoanStage stage;
    private Integer dpd;
    private LocalDate lastRepaymentDate;
    private BigDecimal lastRepaymentAmount;
    private OffsetDateTime createdAt;
}
