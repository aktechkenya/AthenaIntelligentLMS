package com.athena.lms.compliance.dto.request;

import com.athena.lms.compliance.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KycRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    private String checkType = "FULL_KYC";

    private String nationalId;

    private String fullName;

    private String phone;

    private RiskLevel riskLevel = RiskLevel.LOW;
}
