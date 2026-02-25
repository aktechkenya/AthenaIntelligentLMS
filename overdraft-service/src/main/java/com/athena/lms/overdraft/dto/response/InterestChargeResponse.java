package com.athena.lms.overdraft.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class InterestChargeResponse {
    private UUID id;
    private UUID facilityId;
    private LocalDate chargeDate;
    private BigDecimal drawnAmount;
    private BigDecimal dailyRate;
    private BigDecimal interestCharged;
    private String reference;
    private OffsetDateTime createdAt;
}
