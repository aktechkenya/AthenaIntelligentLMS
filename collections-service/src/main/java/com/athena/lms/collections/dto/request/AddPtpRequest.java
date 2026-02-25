package com.athena.lms.collections.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AddPtpRequest {

    @NotNull(message = "Promised amount is required")
    @DecimalMin(value = "0.01", message = "Promised amount must be at least 0.01")
    private BigDecimal promisedAmount;

    @NotNull(message = "Promise date is required")
    @FutureOrPresent(message = "Promise date must be today or in the future")
    private LocalDate promiseDate;

    private String notes;
    private String createdBy;
}
