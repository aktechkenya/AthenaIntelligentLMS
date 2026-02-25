package com.athena.lms.payment.dto.request;

import com.athena.lms.payment.enums.PaymentMethodType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class AddPaymentMethodRequest {
    @NotBlank private String customerId;
    @NotNull private PaymentMethodType methodType;
    @NotBlank private String accountNumber;
    private String accountName;
    private String alias;
    private String provider;
    private Boolean isDefault = false;
}
