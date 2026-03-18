package com.athena.lms.fraud.dto.response;

import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.CaseStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class CaseResponse {
    private UUID id;
    private String tenantId;
    private String caseNumber;
    private String title;
    private String description;
    private CaseStatus status;
    private AlertSeverity priority;
    private String customerId;
    private String assignedTo;
    private BigDecimal totalExposure;
    private BigDecimal confirmedLoss;
    private Set<UUID> alertIds;
    private List<String> tags;
    private String closedBy;
    private String outcome;
    private OffsetDateTime closedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<CaseNoteResponse> notes;
}
