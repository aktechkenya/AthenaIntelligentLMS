package com.athena.lms.compliance.dto.response;

import com.athena.lms.compliance.enums.KycStatus;
import com.athena.lms.compliance.enums.RiskLevel;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class KycResponse {
    private UUID id;
    private String tenantId;
    private String customerId;
    private KycStatus status;
    private String checkType;
    private String nationalId;
    private String fullName;
    private String phone;
    private RiskLevel riskLevel;
    private String failureReason;
    private String checkedBy;
    private OffsetDateTime checkedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
