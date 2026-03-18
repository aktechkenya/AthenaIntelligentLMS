package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.response.FraudAnalyticsResponse;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import com.athena.lms.fraud.repository.FraudCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FraudAnalyticsService {

    private final FraudAlertRepository alertRepository;
    private final FraudCaseRepository caseRepository;

    public FraudAnalyticsResponse getAnalytics(String tenantId, int days) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);
        FraudAnalyticsResponse resp = new FraudAnalyticsResponse();

        long total = alertRepository.countByTenantId(tenantId);
        long resolved = alertRepository.countResolved(tenantId);
        resp.setTotalAlerts(total);
        resp.setResolvedAlerts(resolved);
        resp.setResolutionRate(total > 0 ? (double) resolved / total : 0.0);

        resp.setActiveCases(caseRepository.countActiveCases(tenantId));

        // Rule effectiveness
        Map<String, long[]> ruleStats = new LinkedHashMap<>();
        for (Object[] row : alertRepository.countByRule(tenantId)) {
            ruleStats.put((String) row[0], new long[]{((Number) row[1]).longValue(), 0, 0});
        }
        for (Object[] row : alertRepository.countConfirmedByRule(tenantId)) {
            long[] stats = ruleStats.get((String) row[0]);
            if (stats != null) stats[1] = ((Number) row[1]).longValue();
        }
        for (Object[] row : alertRepository.countFalsePositiveByRule(tenantId)) {
            long[] stats = ruleStats.get((String) row[0]);
            if (stats != null) stats[2] = ((Number) row[1]).longValue();
        }

        long totalConfirmed = 0;
        long totalFP = 0;
        List<FraudAnalyticsResponse.RuleEffectiveness> ruleList = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : ruleStats.entrySet()) {
            long[] s = entry.getValue();
            FraudAnalyticsResponse.RuleEffectiveness re = new FraudAnalyticsResponse.RuleEffectiveness();
            re.setRuleCode(entry.getKey());
            re.setTotalTriggers(s[0]);
            re.setConfirmedFraud(s[1]);
            re.setFalsePositives(s[2]);
            long reviewedByRule = s[1] + s[2];
            re.setPrecisionRate(reviewedByRule > 0 ? (double) s[1] / reviewedByRule : 0.0);
            totalConfirmed += s[1];
            totalFP += s[2];
            ruleList.add(re);
        }
        resp.setRuleEffectiveness(ruleList);
        resp.setConfirmedFraudCount(totalConfirmed);
        resp.setFalsePositiveCount(totalFP);
        long totalReviewed = totalConfirmed + totalFP;
        resp.setPrecisionRate(totalReviewed > 0 ? (double) totalConfirmed / totalReviewed : 0.0);

        // Daily trend
        List<FraudAnalyticsResponse.DailyAlertCount> trend = new ArrayList<>();
        for (Object[] row : alertRepository.countByDay(tenantId, since)) {
            FraudAnalyticsResponse.DailyAlertCount d = new FraudAnalyticsResponse.DailyAlertCount();
            d.setDate(row[0].toString());
            d.setCount(((Number) row[1]).longValue());
            trend.add(d);
        }
        resp.setDailyTrend(trend);

        // Alerts by type
        List<FraudAnalyticsResponse.TypeCount> typeList = new ArrayList<>();
        for (Object[] row : alertRepository.countByAlertType(tenantId, since)) {
            FraudAnalyticsResponse.TypeCount tc = new FraudAnalyticsResponse.TypeCount();
            tc.setType(row[0].toString());
            tc.setCount(((Number) row[1]).longValue());
            typeList.add(tc);
        }
        resp.setAlertsByType(typeList);

        return resp;
    }
}
