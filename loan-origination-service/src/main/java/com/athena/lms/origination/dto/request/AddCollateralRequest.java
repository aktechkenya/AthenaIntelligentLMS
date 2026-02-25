package com.athena.lms.origination.dto.request;

import com.athena.lms.origination.enums.CollateralType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddCollateralRequest {
    @NotNull private CollateralType collateralType;
    @NotBlank private String description;
    @NotNull @Positive private BigDecimal estimatedValue;
    private String currency = "KES";
    private String documentRef;
}
