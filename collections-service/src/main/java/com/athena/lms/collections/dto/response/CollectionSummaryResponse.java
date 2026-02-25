package com.athena.lms.collections.dto.response;

import lombok.Data;

@Data
public class CollectionSummaryResponse {
    private long totalOpenCases;
    private long watchCases;
    private long substandardCases;
    private long doubtfulCases;
    private long lossCases;
    private long criticalPriorityCases;
    private String tenantId;
}
