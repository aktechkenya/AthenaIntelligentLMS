package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.response.BatchScreeningResult;
import com.athena.lms.fraud.entity.*;
import com.athena.lms.fraud.enums.*;
import com.athena.lms.fraud.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchScreeningService {

    private final WatchlistRepository watchlistRepository;
    private final CustomerRiskProfileRepository customerRiskProfileRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final CaseManagementService caseManagementService;

    @Transactional
    public BatchScreeningResult screenAllCustomers(String tenantId) {
        List<WatchlistEntry> activeEntries = watchlistRepository.findAllByTenantIdAndActive(tenantId, true);
        List<CustomerRiskProfile> profiles = customerRiskProfileRepository.findAllByTenantId(tenantId);

        int matchesFound = 0;
        int alertsCreated = 0;
        List<String> matchedCustomerIds = new ArrayList<>();

        for (CustomerRiskProfile profile : profiles) {
            List<WatchlistEntry> matches = findMatchingEntries(profile, activeEntries);
            if (!matches.isEmpty()) {
                matchesFound += matches.size();
                matchedCustomerIds.add(profile.getCustomerId());

                for (WatchlistEntry match : matches) {
                    // Check if a WATCHLIST_MATCH alert already exists for this customer (open)
                    long existingAlerts = fraudAlertRepository.countOpenAlertsByCustomer(tenantId, profile.getCustomerId());
                    // Also check recent watchlist alerts specifically
                    long recentWatchlistAlerts = fraudAlertRepository.countRecentAlertsByRule(
                            tenantId, profile.getCustomerId(), "WATCHLIST_SCREEN",
                            OffsetDateTime.now().minusHours(24));

                    if (recentWatchlistAlerts == 0) {
                        FraudAlert alert = FraudAlert.builder()
                                .tenantId(tenantId)
                                .alertType(AlertType.WATCHLIST_MATCH)
                                .severity(AlertSeverity.HIGH)
                                .status(AlertStatus.OPEN)
                                .source(AlertSource.WATCHLIST)
                                .ruleCode("WATCHLIST_SCREEN")
                                .customerId(profile.getCustomerId())
                                .subjectType("CUSTOMER")
                                .subjectId(profile.getCustomerId())
                                .description("Watchlist match found: " + match.getName()
                                        + " (list: " + match.getListType() + ", reason: " + match.getReason() + ")")
                                .triggerEvent("batch.screening")
                                .build();
                        fraudAlertRepository.save(alert);
                        alertsCreated++;

                        caseManagementService.audit(tenantId, "WATCHLIST_MATCH_FOUND", "ALERT",
                                alert.getId(), "system",
                                "Batch screening match: customer=" + profile.getCustomerId()
                                        + " matched watchlist entry=" + match.getName(),
                                null);
                    }
                }
            }
        }

        log.info("Batch screening completed for tenant={}: screened={}, matches={}, alerts={}",
                tenantId, profiles.size(), matchesFound, alertsCreated);

        return BatchScreeningResult.builder()
                .customersScreened(profiles.size())
                .matchesFound(matchesFound)
                .alertsCreated(alertsCreated)
                .matchedCustomerIds(matchedCustomerIds)
                .build();
    }

    @Transactional(readOnly = true)
    public List<WatchlistEntry> screenCustomer(String tenantId, String customerId,
                                                String name, String nationalId, String phone) {
        return watchlistRepository.findMatches(tenantId,
                nationalId != null ? nationalId : "",
                name != null ? name : "",
                phone != null ? phone : "");
    }

    private List<WatchlistEntry> findMatchingEntries(CustomerRiskProfile profile,
                                                      List<WatchlistEntry> watchlistEntries) {
        List<WatchlistEntry> matches = new ArrayList<>();
        String customerId = profile.getCustomerId();

        for (WatchlistEntry entry : watchlistEntries) {
            boolean matched = false;

            // Match by name (case-insensitive)
            if (entry.getName() != null && customerId != null
                    && entry.getName().equalsIgnoreCase(customerId)) {
                matched = true;
            }

            // Match by nationalId
            if (entry.getNationalId() != null && entry.getNationalId().equals(customerId)) {
                matched = true;
            }

            // Match by phone
            if (entry.getPhone() != null && entry.getPhone().equals(customerId)) {
                matched = true;
            }

            if (matched) {
                matches.add(entry);
            }
        }
        return matches;
    }
}
