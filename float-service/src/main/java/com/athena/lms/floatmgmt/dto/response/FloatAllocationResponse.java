package com.athena.lms.floatmgmt.dto.response;

import com.athena.lms.floatmgmt.enums.FloatAllocationStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class FloatAllocationResponse {
    private UUID id;
    private UUID floatAccountId;
    private UUID loanId;
    private BigDecimal allocatedAmount;
    private BigDecimal repaidAmount;
    private BigDecimal outstanding;
    private FloatAllocationStatus status;
    private OffsetDateTime disbursedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime createdAt;
}
