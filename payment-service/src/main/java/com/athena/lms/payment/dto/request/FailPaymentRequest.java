package com.athena.lms.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FailPaymentRequest {
    @NotBlank private String reason;
}
