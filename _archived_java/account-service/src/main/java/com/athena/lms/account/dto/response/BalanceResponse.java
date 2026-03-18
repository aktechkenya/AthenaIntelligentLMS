package com.athena.lms.account.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BalanceResponse {
    private BigDecimal availableBalance;
    private BigDecimal currentBalance;
    private BigDecimal ledgerBalance;
    private LocalDateTime updatedAt;
}
