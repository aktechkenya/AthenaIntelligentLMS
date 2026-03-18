package com.athena.lms.fraud.dto.response;

import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.RuleCategory;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class RuleResponse {
    private UUID id;
    private String ruleCode;
    private String ruleName;
    private String description;
    private RuleCategory category;
    private AlertSeverity severity;
    private String eventTypes;
    private Boolean enabled;
    private Map<String, Object> parameters;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
