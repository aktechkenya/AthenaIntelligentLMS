package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class OverdraftFacilityResponse {
    private UUID id;
    private String tenantId;
    private UUID walletId;
    private String customerId;
    private Integer creditScore;
    private String creditBand;
    private BigDecimal approvedLimit;
    private BigDecimal drawnAmount;
    private BigDecimal availableOverdraft;
    private BigDecimal interestRate;
    private String status;
    private OffsetDateTime appliedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime createdAt;
}
