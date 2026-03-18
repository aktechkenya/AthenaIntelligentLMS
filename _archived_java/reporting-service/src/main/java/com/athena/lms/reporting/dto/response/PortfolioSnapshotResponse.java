package com.athena.lms.reporting.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class PortfolioSnapshotResponse {
    private UUID id;
    private String tenantId;
    private LocalDate snapshotDate;
    private String period;
    private Integer totalLoans;
    private Integer activeLoans;
    private Integer closedLoans;
    private Integer defaultedLoans;
    private BigDecimal totalDisbursed;
    private BigDecimal totalOutstanding;
    private BigDecimal totalCollected;
    private Integer watchLoans;
    private Integer substandardLoans;
    private Integer doubtfulLoans;
    private Integer lossLoans;
    private BigDecimal par30;
    private BigDecimal par90;
    private Instant createdAt;
}
