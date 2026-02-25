package com.athena.lms.floatmgmt.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FloatSummaryResponse {
    private BigDecimal totalLimit;
    private BigDecimal totalDrawn;
    private BigDecimal totalAvailable;
    private int activeAccounts;
    private int activeAllocations;
    private String tenantId;
}
