package com.athena.lms.accounting.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder
public class JournalLineResponse {
    private UUID id;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private Integer lineNo;
    private String description;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String currency;
}
