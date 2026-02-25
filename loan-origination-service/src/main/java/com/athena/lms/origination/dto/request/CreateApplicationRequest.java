package com.athena.lms.origination.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateApplicationRequest {
    @NotBlank private String customerId;
    @NotNull private UUID productId;
    @NotNull @Positive private BigDecimal requestedAmount;
    @NotNull @Min(1) @Max(360) private Integer tenorMonths;
    private String purpose;
    private String currency = "KES";
    private String disbursementAccount;
}
