package com.athena.lms.fraud.dto.response;

import com.athena.lms.fraud.enums.RiskLevel;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class CustomerRiskResponse {
    private String customerId;
    private String tenantId;
    private BigDecimal riskScore;
    private RiskLevel riskLevel;
    private Integer totalAlerts;
    private Integer openAlerts;
    private Integer confirmedFraud;
    private Integer falsePositives;
    private OffsetDateTime lastAlertAt;
    private Map<String, Object> factors;
}
