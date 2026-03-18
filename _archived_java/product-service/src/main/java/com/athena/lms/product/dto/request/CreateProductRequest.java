package com.athena.lms.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank(message = "productCode is required")
    private String productCode;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "productType is required")
    private String productType;

    private String description;
    private String currency = "KES";

    private BigDecimal minAmount;

    @NotNull(message = "maxAmount is required")
    @DecimalMin("1.00")
    private BigDecimal maxAmount;

    private Integer minTenorDays;

    @NotNull(message = "maxTenorDays is required")
    private Integer maxTenorDays;

    private String scheduleType = "EMI";
    private String repaymentFrequency = "MONTHLY";

    @NotNull(message = "nominalRate is required")
    @DecimalMin(value = "0.0")
    private BigDecimal nominalRate;

    private BigDecimal penaltyRate = BigDecimal.ZERO;
    private Integer penaltyGraceDays = 1;
    private Integer gracePeriodDays = 0;
    private BigDecimal processingFeeRate = BigDecimal.ZERO;
    private BigDecimal processingFeeMin = BigDecimal.ZERO;
    private BigDecimal processingFeeMax;
    private boolean requiresCollateral = false;
    private int minCreditScore = 0;
    private BigDecimal maxDtir = new BigDecimal("100.00");

    @Valid
    private List<FeeRequest> fees = new ArrayList<>();

    private boolean requiresTwoPersonAuth = false;
    private BigDecimal authThresholdAmount;
    private String templateId;

    @Data
    public static class FeeRequest {
        @NotBlank
        private String feeName;
        @NotBlank
        private String feeType;
        @NotBlank
        private String calculationType;
        private BigDecimal amount;
        private BigDecimal rate;
        private boolean isMandatory = true;
    }
}
