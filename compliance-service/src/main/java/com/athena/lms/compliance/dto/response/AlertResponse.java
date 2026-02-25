package com.athena.lms.compliance.dto.response;

import com.athena.lms.compliance.enums.AlertSeverity;
import com.athena.lms.compliance.enums.AlertStatus;
import com.athena.lms.compliance.enums.AlertType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class AlertResponse {
    private UUID id;
    private String tenantId;
    private AlertType alertType;
    private AlertSeverity severity;
    private AlertStatus status;
    private String subjectType;
    private String subjectId;
    private String customerId;
    private String description;
    private String triggerEvent;
    private BigDecimal triggerAmount;
    private Boolean sarFiled;
    private String sarReference;
    private String assignedTo;
    private String resolvedBy;
    private OffsetDateTime resolvedAt;
    private String resolutionNotes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
