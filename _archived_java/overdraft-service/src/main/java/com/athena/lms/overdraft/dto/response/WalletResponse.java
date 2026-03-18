package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class WalletResponse {
    private UUID id;
    private String tenantId;
    private String customerId;
    private String accountNumber;
    private String currency;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
