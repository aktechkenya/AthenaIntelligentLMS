package com.athena.lms.fraud.dto.response;

import com.athena.lms.fraud.enums.SarReportType;
import com.athena.lms.fraud.enums.SarStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class SarReportResponse {
    private UUID id;
    private String tenantId;
    private String reportNumber;
    private SarReportType reportType;
    private SarStatus status;
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
    private String reviewedBy;
    private String filedBy;
    private OffsetDateTime filedAt;
    private String filingReference;
    private String regulator;
    private OffsetDateTime filingDeadline;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
