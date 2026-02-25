package com.athena.lms.collections.dto.response;

import com.athena.lms.collections.enums.CasePriority;
import com.athena.lms.collections.enums.CaseStatus;
import com.athena.lms.collections.enums.CollectionStage;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CollectionCaseResponse {
    private UUID id;
    private String tenantId;
    private UUID loanId;
    private Long customerId;
    private String caseNumber;
    private CaseStatus status;
    private CasePriority priority;
    private int currentDpd;
    private CollectionStage currentStage;
    private BigDecimal outstandingAmount;
    private String assignedTo;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime lastActionAt;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
