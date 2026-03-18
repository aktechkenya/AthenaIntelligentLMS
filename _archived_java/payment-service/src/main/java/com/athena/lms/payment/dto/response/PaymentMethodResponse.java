package com.athena.lms.payment.dto.response;

import com.athena.lms.payment.enums.PaymentMethodType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class PaymentMethodResponse {
    private UUID id;
    private String customerId;
    private PaymentMethodType methodType;
    private String alias;
    private String accountNumber;
    private String accountName;
    private String provider;
    private Boolean isDefault;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
