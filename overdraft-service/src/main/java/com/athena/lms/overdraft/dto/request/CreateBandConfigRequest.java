package com.athena.lms.overdraft.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateBandConfigRequest {
    @NotBlank
    private String band;

    @NotNull
    private Integer minScore;

    @NotNull
    private Integer maxScore;

    @NotNull
    private BigDecimal approvedLimit;

    @NotNull
    private BigDecimal interestRate;

    private BigDecimal arrangementFee = BigDecimal.ZERO;
    private BigDecimal annualFee = BigDecimal.ZERO;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
