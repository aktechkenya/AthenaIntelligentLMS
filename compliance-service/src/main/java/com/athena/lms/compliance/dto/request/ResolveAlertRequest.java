package com.athena.lms.compliance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveAlertRequest {

    @NotBlank(message = "Resolved by is required")
    private String resolvedBy;

    @NotBlank(message = "Resolution notes are required")
    private String resolutionNotes;
}
