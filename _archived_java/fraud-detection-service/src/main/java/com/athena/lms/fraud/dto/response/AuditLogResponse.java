package com.athena.lms.fraud.dto.response;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditLogResponse {
    private UUID id;
    private String action;
    private String entityType;
    private UUID entityId;
    private String performedBy;
    private String description;
    private Map<String, Object> changes;
    private OffsetDateTime createdAt;
}
