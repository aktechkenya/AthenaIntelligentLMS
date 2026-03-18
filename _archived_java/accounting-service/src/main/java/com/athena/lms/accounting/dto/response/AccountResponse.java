package com.athena.lms.accounting.dto.response;

import com.athena.lms.accounting.enums.AccountType;
import com.athena.lms.accounting.enums.BalanceType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class AccountResponse {
    private UUID id;
    private String tenantId;
    private String code;
    private String name;
    private AccountType accountType;
    private BalanceType balanceType;
    private UUID parentId;
    private String description;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
