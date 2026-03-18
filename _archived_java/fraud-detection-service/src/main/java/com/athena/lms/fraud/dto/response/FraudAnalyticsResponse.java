package com.athena.lms.fraud.dto.response;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FraudAnalyticsResponse {
    private long totalAlerts;
    private long resolvedAlerts;
    private double resolutionRate;
    private long activeCases;
    private long confirmedFraudCount;
    private long falsePositiveCount;
    private double precisionRate;
    private List<RuleEffectiveness> ruleEffectiveness;
    private List<DailyAlertCount> dailyTrend;
    private List<TypeCount> alertsByType;

    @Data
    public static class RuleEffectiveness {
        private String ruleCode;
        private long totalTriggers;
        private long confirmedFraud;
        private long falsePositives;
        private double precisionRate;
    }

    @Data
    public static class DailyAlertCount {
        private String date;
        private long count;
    }

    @Data
    public static class TypeCount {
        private String type;
        private long count;
    }
}
