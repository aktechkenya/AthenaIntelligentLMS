package com.athena.lms.collections.dto.response;

import com.athena.lms.collections.enums.ActionOutcome;
import com.athena.lms.collections.enums.ActionType;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CollectionActionResponse {
    private UUID id;
    private UUID caseId;
    private ActionType actionType;
    private ActionOutcome outcome;
    private String notes;
    private String contactPerson;
    private String contactMethod;
    private String performedBy;
    private OffsetDateTime performedAt;
    private LocalDate nextActionDate;
    private OffsetDateTime createdAt;
}
