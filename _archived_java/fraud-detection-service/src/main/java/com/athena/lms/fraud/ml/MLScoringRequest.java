package com.athena.lms.fraud.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLScoringRequest {

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("rule_score")
    private Double ruleScore;
}
