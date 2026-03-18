package com.athena.lms.floatmgmt.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateFloatAccountRequest {

    @NotBlank
    private String accountName;

    @NotBlank
    private String accountCode;

    private String currency = "KES";

    @NotNull
    @DecimalMin("0")
    private BigDecimal floatLimit;

    private String description;
}
