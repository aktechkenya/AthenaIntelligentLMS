package com.athena.lms.collections.dto.request;

import com.athena.lms.collections.enums.ActionOutcome;
import com.athena.lms.collections.enums.ActionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AddActionRequest {

    @NotNull(message = "Action type is required")
    private ActionType actionType;

    private ActionOutcome outcome;
    private String notes;
    private String contactPerson;
    private String contactMethod;
    private String performedBy;
    private LocalDate nextActionDate;
}
