package com.athena.lms.floatmgmt.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FloatRepayRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    private String referenceId;
    private String narration;
}
