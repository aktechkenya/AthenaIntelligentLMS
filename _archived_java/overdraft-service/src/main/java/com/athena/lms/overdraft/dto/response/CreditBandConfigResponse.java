package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CreditBandConfigResponse {
    private UUID id;
    private String tenantId;
    private String band;
    private Integer minScore;
    private Integer maxScore;
    private BigDecimal approvedLimit;
    private BigDecimal interestRate;
    private BigDecimal arrangementFee;
    private BigDecimal annualFee;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
