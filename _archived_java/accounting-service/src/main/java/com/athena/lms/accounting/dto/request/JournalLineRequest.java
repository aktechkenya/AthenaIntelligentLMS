package com.athena.lms.accounting.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class JournalLineRequest {
    @NotNull private UUID accountId;
    private String description;
    @NotNull private BigDecimal debitAmount;
    @NotNull private BigDecimal creditAmount;
    private String currency = "KES";
}
