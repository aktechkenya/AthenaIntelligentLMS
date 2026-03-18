package com.athena.lms.fraud.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class UpdateRuleRequest {
    private String severity;
    private Boolean enabled;
    private Map<String, Object> parameters;
}
