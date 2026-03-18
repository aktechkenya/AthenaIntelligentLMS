package com.athena.lms.fraud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveAlertRequest {
    @NotBlank
    private String resolvedBy;
    private Boolean confirmedFraud;
    private String notes;
}
