package com.athena.lms.collections.dto.request;

import com.athena.lms.collections.enums.CasePriority;
import lombok.Data;

@Data
public class UpdateCaseRequest {
    private String assignedTo;
    private CasePriority priority;
    private String notes;
}
