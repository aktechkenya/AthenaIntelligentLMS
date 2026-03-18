package com.athena.lms.floatmgmt.dto.response;

import com.athena.lms.floatmgmt.enums.FloatTransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class FloatTransactionResponse {
    private UUID id;
    private UUID floatAccountId;
    private FloatTransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String referenceId;
    private String referenceType;
    private String narration;
    private OffsetDateTime createdAt;
}
