package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class BillingStatementResponse {
    private UUID id;
    private UUID facilityId;
    private LocalDate billingDate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal openingBalance;
    private BigDecimal interestAccrued;
    private BigDecimal feesCharged;
    private BigDecimal paymentsReceived;
    private BigDecimal closingBalance;
    private BigDecimal minimumPaymentDue;
    private LocalDate dueDate;
    private String status;
    private OffsetDateTime createdAt;
}
