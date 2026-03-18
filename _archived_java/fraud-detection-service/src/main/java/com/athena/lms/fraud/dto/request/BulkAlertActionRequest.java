package com.athena.lms.fraud.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Set;
import java.util.UUID;

@Data
public class BulkAlertActionRequest {
    @NotEmpty
    private Set<UUID> alertIds;
    @NotBlank
    private String performedBy;
    private String notes;
}
