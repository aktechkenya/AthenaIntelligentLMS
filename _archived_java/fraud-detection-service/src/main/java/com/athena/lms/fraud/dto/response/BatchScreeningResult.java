package com.athena.lms.fraud.dto.response;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchScreeningResult {
    private int customersScreened;
    private int matchesFound;
    private int alertsCreated;
    private List<String> matchedCustomerIds;
}
