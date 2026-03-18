package com.athena.lms.scoring.dto.response;

import com.athena.lms.scoring.enums.ScoringStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ScoringRequestResponse {
    private UUID id;
    private String tenantId;
    private UUID loanApplicationId;
    private Long customerId;
    private ScoringStatus status;
    private String triggerEvent;
    private Instant requestedAt;
    private Instant completedAt;
    private String errorMessage;
    private Instant createdAt;
}
