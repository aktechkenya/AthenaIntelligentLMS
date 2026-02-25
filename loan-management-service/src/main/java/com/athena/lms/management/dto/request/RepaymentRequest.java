package com.athena.lms.management.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RepaymentRequest {
    @NotNull @Positive private BigDecimal amount;
    @NotNull private LocalDate paymentDate;
    private String paymentReference;
    private String paymentMethod;
    private String currency = "KES";
}
