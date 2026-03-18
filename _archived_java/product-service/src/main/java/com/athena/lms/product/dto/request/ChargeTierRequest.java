package com.athena.lms.product.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChargeTierRequest {
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
    private BigDecimal flatAmount;
    private BigDecimal percentageRate;
}
