package com.athena.lms.fraud.scheduler;

import com.athena.lms.fraud.entity.FraudCase;
import com.athena.lms.fraud.entity.SarReport;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.repository.FraudCaseRepository;
import com.athena.lms.fraud.repository.SarReportRepository;
import com.athena.lms.fraud.repository.WatchlistRepository;
import com.athena.lms.fraud.service.CaseManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledFraudTasks {

    private final WatchlistRepository watchlistRepository;
    private final SarReportRepository sarReportRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final CaseManagementService caseManagementService;

    /**
     * Deactivate expired watchlist entries every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredWatchlistEntries() {
        List<WatchlistEntry> expired = watchlistRepository.findExpiredEntries(OffsetDateTime.now());
        for (WatchlistEntry entry : expired) {
            entry.setActive(false);
            watchlistRepository.save(entry);
            log.info("Deactivated expired watchlist entry: id={} name={}", entry.getId(), entry.getName());
        }
        if (!expired.isEmpty()) {
            log.info("Deactivated {} expired watchlist entries", expired.size());
        }
    }

    /**
     * Check for overdue SAR filing deadlines daily at 8am.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional(readOnly = true)
    public void checkOverdueSarDeadlines() {
        List<SarReport> overdue = sarReportRepository.findOverdueReports(OffsetDateTime.now());
        for (SarReport report : overdue) {
            log.warn("SAR OVERDUE: report={} deadline={} status={} tenant={}",
                    report.getReportNumber(), report.getFilingDeadline(),
                    report.getStatus(), report.getTenantId());
            // Audit the overdue event
            caseManagementService.audit(report.getTenantId(), "SAR_OVERDUE", "SAR_REPORT",
                    report.getId(), "system",
                    "SAR filing deadline exceeded: " + report.getReportNumber(), null);
        }
        if (!overdue.isEmpty()) {
            log.warn("{} SAR reports are past their filing deadline!", overdue.size());
        }
    }

    /**
     * Check for cases that have breached their SLA deadline every hour.
     */
    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void checkOverdueCaseSLA() {
        List<FraudCase> overdueCases = fraudCaseRepository.findOverdueCases(OffsetDateTime.now());
        for (FraudCase fraudCase : overdueCases) {
            fraudCase.setSlaBreached(true);
            fraudCaseRepository.save(fraudCase);
            log.warn("SLA BREACHED: case={} deadline={} status={} tenant={}",
                    fraudCase.getCaseNumber(), fraudCase.getSlaDeadline(),
                    fraudCase.getStatus(), fraudCase.getTenantId());
            caseManagementService.audit(fraudCase.getTenantId(), "SLA_BREACHED", "CASE",
                    fraudCase.getId(), "system",
                    "SLA deadline breached for case: " + fraudCase.getCaseNumber(), null);
        }
        if (!overdueCases.isEmpty()) {
            log.warn("{} fraud cases have breached their SLA deadline!", overdueCases.size());
        }
    }
}
