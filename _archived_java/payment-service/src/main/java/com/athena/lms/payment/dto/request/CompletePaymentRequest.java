package com.athena.lms.payment.dto.request;

import lombok.Data;

@Data
public class CompletePaymentRequest {
    private String externalReference;
    private String notes;
}
