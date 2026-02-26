package com.athena.lms.account.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {

    @NotNull(message = "sourceAccountId is required")
    private UUID sourceAccountId;

    private UUID destinationAccountId;

    private String destinationAccountNumber;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be > 0")
    private BigDecimal amount;

    @NotBlank(message = "transferType is required")
    private String transferType;  // INTERNAL | THIRD_PARTY | WALLET

    private String narration;

    private String idempotencyKey;
}
