package com.athena.lms.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ChargeCalculationResponse {
    private String chargeCode;
    private String chargeName;
    private String transactionType;
    private BigDecimal transactionAmount;
    private BigDecimal chargeAmount;
    private String currency;
}
