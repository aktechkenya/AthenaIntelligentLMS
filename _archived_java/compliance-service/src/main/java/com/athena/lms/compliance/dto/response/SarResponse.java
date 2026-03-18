package com.athena.lms.compliance.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class SarResponse {
    private UUID id;
    private String tenantId;
    private UUID alertId;
    private String referenceNumber;
    private LocalDate filingDate;
    private String regulator;
    private String status;
    private String submittedBy;
    private String notes;
    private OffsetDateTime createdAt;
}
