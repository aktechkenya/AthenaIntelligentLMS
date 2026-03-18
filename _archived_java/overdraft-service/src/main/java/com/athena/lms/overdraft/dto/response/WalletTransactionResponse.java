package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class WalletTransactionResponse {
    private UUID id;
    private UUID walletId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String reference;
    private String description;
    private OffsetDateTime createdAt;
}
