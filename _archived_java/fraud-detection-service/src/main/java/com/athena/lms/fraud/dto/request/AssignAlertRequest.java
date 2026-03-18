package com.athena.lms.fraud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignAlertRequest {
    @NotBlank
    private String assignee;
}
