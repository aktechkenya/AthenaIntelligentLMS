package com.athena.lms.product.dto.request;

import com.athena.lms.product.enums.RepaymentFrequency;
import com.athena.lms.product.enums.ScheduleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SimulateScheduleRequest {

    @NotNull(message = "principal is required")
    @DecimalMin("1.00")
    private BigDecimal principal;

    @NotNull(message = "nominalRate is required")
    @DecimalMin("0.0")
    private BigDecimal nominalRate;

    @NotNull @Positive
    private Integer tenorDays;

    @NotNull(message = "scheduleType is required")
    private ScheduleType scheduleType;

    private RepaymentFrequency repaymentFrequency = RepaymentFrequency.MONTHLY;

    private LocalDate disbursementDate = LocalDate.now();
}
