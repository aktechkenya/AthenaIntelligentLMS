package com.athena.lms.origination.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApproveApplicationRequest {
    @NotNull @Positive private BigDecimal approvedAmount;
    @NotNull @Positive private BigDecimal interestRate;
    private String riskGrade;
    private Integer creditScore;
    private String reviewNotes;
}
