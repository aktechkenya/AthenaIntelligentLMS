package com.athena.lms.fraud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class CreateCaseRequest {
    @NotBlank
    private String title;
    private String description;
    private String priority;
    private String customerId;
    private String assignedTo;
    private BigDecimal totalExposure;
    private Set<UUID> alertIds;
    private List<String> tags;
}
