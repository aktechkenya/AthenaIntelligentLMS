package com.athena.lms.account.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotNull(message = "customerId is required")
    private Long customerId;

    @NotBlank(message = "accountType is required")
    private String accountType;   // CURRENT | SAVINGS | WALLET

    private String currency = "KES";

    @Min(0) @Max(3)
    private int kycTier = 0;

    private String accountName;
}
