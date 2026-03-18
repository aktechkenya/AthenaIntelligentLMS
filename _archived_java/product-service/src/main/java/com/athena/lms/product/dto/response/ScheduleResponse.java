package com.athena.lms.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ScheduleResponse {
    private String scheduleType;
    private BigDecimal principal;
    private BigDecimal totalInterest;
    private BigDecimal totalPayable;
    private BigDecimal effectiveRate;
    private int numberOfInstallments;
    private List<InstallmentResponse> installments;
}
