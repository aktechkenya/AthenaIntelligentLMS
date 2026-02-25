package com.athena.lms.compliance.dto.request;

import com.athena.lms.compliance.enums.AlertSeverity;
import com.athena.lms.compliance.enums.AlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAlertRequest {

    @NotNull(message = "Alert type is required")
    private AlertType alertType;

    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @NotBlank(message = "Subject type is required")
    private String subjectType;

    @NotBlank(message = "Subject ID is required")
    private String subjectId;

    private Long customerId;

    @NotBlank(message = "Description is required")
    private String description;

    private String triggerEvent;

    private BigDecimal triggerAmount;
}
