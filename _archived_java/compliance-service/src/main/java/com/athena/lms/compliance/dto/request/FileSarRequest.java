package com.athena.lms.compliance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FileSarRequest {

    @NotBlank(message = "Reference number is required")
    private String referenceNumber;

    private LocalDate filingDate;

    private String regulator;

    private String submittedBy;

    private String notes;
}
