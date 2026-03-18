package com.athena.lms.product.dto.response;

import com.athena.lms.product.entity.ProductFee;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ProductFeeResponse {
    private UUID id;
    private String feeName;
    private String feeType;
    private String calculationType;
    private BigDecimal amount;
    private BigDecimal rate;
    private boolean isMandatory;

    public static ProductFeeResponse from(ProductFee fee) {
        return ProductFeeResponse.builder()
                .id(fee.getId())
                .feeName(fee.getFeeName())
                .feeType(fee.getFeeType().name())
                .calculationType(fee.getCalculationType().name())
                .amount(fee.getAmount())
                .rate(fee.getRate())
                .isMandatory(fee.isMandatory())
                .build();
    }
}
