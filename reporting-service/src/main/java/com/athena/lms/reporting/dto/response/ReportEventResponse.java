package com.athena.lms.reporting.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class ReportEventResponse {
    private UUID id;
    private String tenantId;
    private String eventId;
    private String eventType;
    private String eventCategory;
    private String sourceService;
    private String subjectId;
    private Long customerId;
    private BigDecimal amount;
    private String currency;
    private String payload;
    private Instant occurredAt;
    private Instant createdAt;
}
