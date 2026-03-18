package com.athena.lms.fraud.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class UpdateCaseRequest {
    private String status;
    private String priority;
    private String assignedTo;
    private BigDecimal totalExposure;
    private BigDecimal confirmedLoss;
    private Set<UUID> alertIds;
    private List<String> tags;
    private String outcome;
    private String closedBy;
}
