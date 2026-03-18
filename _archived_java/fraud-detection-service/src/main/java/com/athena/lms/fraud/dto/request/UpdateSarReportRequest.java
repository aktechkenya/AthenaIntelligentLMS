package com.athena.lms.fraud.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class UpdateSarReportRequest {
    private String status; // PENDING_REVIEW, APPROVED, FILED, REJECTED
    private String narrative;
    private BigDecimal suspiciousAmount;
    private OffsetDateTime activityStartDate;
    private OffsetDateTime activityEndDate;
    private Set<UUID> alertIds;
    private String reviewedBy;
    private String filedBy;
    private String filingReference;
}
