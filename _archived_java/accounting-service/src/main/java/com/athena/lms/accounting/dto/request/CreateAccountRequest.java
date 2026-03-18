package com.athena.lms.accounting.dto.request;

import com.athena.lms.accounting.enums.AccountType;
import com.athena.lms.accounting.enums.BalanceType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAccountRequest {
    @NotBlank @Size(max = 20) private String code;
    @NotBlank @Size(max = 200) private String name;
    @NotNull private AccountType accountType;
    @NotNull private BalanceType balanceType;
    private UUID parentId;
    private String description;
}
