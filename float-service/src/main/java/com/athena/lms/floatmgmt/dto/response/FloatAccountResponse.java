package com.athena.lms.floatmgmt.dto.response;

import com.athena.lms.floatmgmt.enums.FloatAccountStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class FloatAccountResponse {
    private UUID id;
    private String tenantId;
    private String accountName;
    private String accountCode;
    private String currency;
    private BigDecimal floatLimit;
    private BigDecimal drawnAmount;
    private BigDecimal available;
    private FloatAccountStatus status;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
