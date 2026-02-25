package com.athena.lms.origination.dto.response;

import com.athena.lms.origination.enums.CollateralType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class CollateralResponse {
    private UUID id;
    private CollateralType collateralType;
    private String description;
    private BigDecimal estimatedValue;
    private String currency;
    private String documentRef;
    private OffsetDateTime createdAt;
}
