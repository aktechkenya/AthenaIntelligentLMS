package com.athena.lms.management.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder
public class InstallmentResponse {
    private UUID id;
    private Integer installmentNo;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal feeDue;
    private BigDecimal penaltyDue;
    private BigDecimal totalDue;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    private BigDecimal feePaid;
    private BigDecimal penaltyPaid;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private String status;
    private LocalDate paidDate;
}
