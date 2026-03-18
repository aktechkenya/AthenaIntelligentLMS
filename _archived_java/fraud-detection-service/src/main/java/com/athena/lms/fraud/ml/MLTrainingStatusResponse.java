package com.athena.lms.fraud.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLTrainingStatusResponse {

    private Map<String, Object> anomaly;
    private Map<String, Object> lgbm;
}
