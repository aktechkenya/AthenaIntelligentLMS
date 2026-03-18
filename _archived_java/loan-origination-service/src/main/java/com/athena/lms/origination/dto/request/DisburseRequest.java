package com.athena.lms.origination.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DisburseRequest {
    @NotNull @Positive private BigDecimal disbursedAmount;
    @NotBlank private String disbursementAccount;
}
