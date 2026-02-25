package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class OverdraftSummaryResponse {
    private long totalFacilities;
    private long activeFacilities;
    private BigDecimal totalApprovedLimit;
    private BigDecimal totalDrawnAmount;
    private BigDecimal totalAvailableOverdraft;
    private Map<String, Long> facilitiesByBand;
    private Map<String, BigDecimal> drawnByBand;
}
