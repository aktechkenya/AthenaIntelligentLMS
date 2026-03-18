package com.athena.lms.fraud.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class CreateSarReportRequest {
    private String reportType; // SAR or CTR
    private String subjectCustomerId;
    private String subjectName;
    private String subjectNationalId;
    private String narrative;
    private BigDecimal suspiciousAmount;
    private OffsetDateTime activityStartDate;
    private OffsetDateTime activityEndDate;
    private Set<UUID> alertIds;
    private UUID caseId;
    private String preparedBy;
}
