package com.athena.lms.reporting.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PortfolioSummaryResponse {
    private String tenantId;
    private LocalDate asOfDate;
    private Integer totalLoans;
    private Integer activeLoans;
    private Integer closedLoans;
    private Integer defaultedLoans;
    private BigDecimal totalDisbursed;
    private BigDecimal totalOutstanding;
    private BigDecimal totalCollected;
    private BigDecimal par30;
    private BigDecimal par90;
    private Integer watchLoans;
    private Integer substandardLoans;
    private Integer doubtfulLoans;
    private Integer lossLoans;
}
