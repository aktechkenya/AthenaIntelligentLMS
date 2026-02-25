package com.athena.lms.payment.dto.request;

import com.athena.lms.payment.enums.PaymentChannel;
import com.athena.lms.payment.enums.PaymentType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InitiatePaymentRequest {
    @NotNull private UUID customerId;
    @NotNull private PaymentType paymentType;
    @NotNull private PaymentChannel paymentChannel;
    @NotNull @Positive private BigDecimal amount;
    private String currency = "KES";
    private UUID loanId;
    private UUID applicationId;
    private UUID paymentMethodId;
    private String externalReference;
    private String description;
}
