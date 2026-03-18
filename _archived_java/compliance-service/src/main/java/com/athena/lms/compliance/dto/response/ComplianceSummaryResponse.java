package com.athena.lms.compliance.dto.response;

import lombok.Data;

@Data
public class ComplianceSummaryResponse {
    private long openAlerts;
    private long criticalAlerts;
    private long underReviewAlerts;
    private long sarFiledAlerts;
    private long pendingKyc;
    private long failedKyc;
    private String tenantId;
}
