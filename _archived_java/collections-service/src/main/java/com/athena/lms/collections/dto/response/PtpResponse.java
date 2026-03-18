package com.athena.lms.collections.dto.response;

import com.athena.lms.collections.enums.PtpStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class PtpResponse {
    private UUID id;
    private UUID caseId;
    private BigDecimal promisedAmount;
    private LocalDate promiseDate;
    private PtpStatus status;
    private String notes;
    private String createdBy;
    private OffsetDateTime fulfilledAt;
    private OffsetDateTime brokenAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
