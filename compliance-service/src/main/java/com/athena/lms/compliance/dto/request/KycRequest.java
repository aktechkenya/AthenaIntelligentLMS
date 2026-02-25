package com.athena.lms.compliance.dto.request;

import com.athena.lms.compliance.enums.RiskLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KycRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    private String checkType = "FULL_KYC";

    private String nationalId;

    private String fullName;

    private String phone;

    private RiskLevel riskLevel = RiskLevel.LOW;
}
