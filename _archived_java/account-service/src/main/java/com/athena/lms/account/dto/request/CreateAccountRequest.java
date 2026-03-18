package com.athena.lms.account.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "accountType is required")
    private String accountType;   // CURRENT | SAVINGS | WALLET

    private String currency = "KES";

    @Min(0) @Max(3)
    private int kycTier = 0;

    private String accountName;
}
