package com.athena.lms.management.dto.request;

import com.athena.lms.management.enums.RepaymentFrequency;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RestructureRequest {
    @NotNull @Min(1) @Max(360) private Integer newTenorMonths;
    @NotNull @Positive private java.math.BigDecimal newInterestRate;
    private RepaymentFrequency newFrequency;
    @NotBlank private String reason;
}
