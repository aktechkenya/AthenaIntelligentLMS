package com.athena.lms.accounting.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder
public class BalanceResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private String balanceType;
    private BigDecimal balance;
    private String currency;
    private Integer periodYear;
    private Integer periodMonth;
}
