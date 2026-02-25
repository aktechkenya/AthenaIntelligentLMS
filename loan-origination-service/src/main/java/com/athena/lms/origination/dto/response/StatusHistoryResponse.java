package com.athena.lms.origination.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class StatusHistoryResponse {
    private UUID id;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String changedBy;
    private OffsetDateTime changedAt;
}
