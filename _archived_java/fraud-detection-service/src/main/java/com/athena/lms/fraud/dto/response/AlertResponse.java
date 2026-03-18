package com.athena.lms.fraud.dto.response;

import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.AlertSource;
import com.athena.lms.fraud.enums.AlertStatus;
import com.athena.lms.fraud.enums.AlertType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AlertResponse {
    private UUID id;
    private String tenantId;
    private AlertType alertType;
    private AlertSeverity severity;
    private AlertStatus status;
    private AlertSource source;
    private String ruleCode;
    private String customerId;
    private String subjectType;
    private String subjectId;
    private String description;
    private String triggerEvent;
    private BigDecimal triggerAmount;
    private BigDecimal riskScore;
    private Boolean escalated;
    private Boolean escalatedToCompliance;
    private String assignedTo;
    private String resolvedBy;
    private OffsetDateTime resolvedAt;
    private String resolution;
    private String resolutionNotes;
    private Map<String, Object> explanation;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
