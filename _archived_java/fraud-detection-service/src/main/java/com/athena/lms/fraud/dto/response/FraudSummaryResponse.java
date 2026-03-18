package com.athena.lms.fraud.dto.response;

import lombok.Data;

@Data
public class FraudSummaryResponse {
    private String tenantId;
    private long openAlerts;
    private long underReviewAlerts;
    private long escalatedAlerts;
    private long confirmedFraud;
    private long criticalAlerts;
    private long highRiskCustomers;
    private long criticalRiskCustomers;
}
