package com.athena.lms.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateChargeRequest {

    @NotBlank(message = "chargeCode is required")
    private String chargeCode;

    @NotBlank(message = "chargeName is required")
    private String chargeName;

    @NotBlank(message = "transactionType is required")
    private String transactionType;

    @NotBlank(message = "calculationType is required")
    private String calculationType;

    private BigDecimal flatAmount;
    private BigDecimal percentageRate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String currency;

    private List<ChargeTierRequest> tiers;
}
