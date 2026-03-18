package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditLogResponse {
    private UUID id;
    private String tenantId;
    private String entityType;
    private UUID entityId;
    private String action;
    private String actor;
    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
}
