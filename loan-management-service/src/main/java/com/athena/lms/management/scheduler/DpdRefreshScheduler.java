package com.athena.lms.management.scheduler;

import com.athena.lms.management.service.LoanManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DpdRefreshScheduler {

    private final LoanManagementService loanManagementService;

    /**
     * Runs daily at 01:00 AM to refresh DPD for all active loans.
     * Cron expression read from application.yml: dpd.refresh.cron
     */
    @Scheduled(cron = "${dpd.refresh.cron:0 0 1 * * *}")
    public void refreshDpd() {
        log.info("Starting daily DPD refresh job");
        try {
            loanManagementService.refreshAllDpd();
            log.info("DPD refresh job completed successfully");
        } catch (Exception e) {
            log.error("DPD refresh job failed: {}", e.getMessage(), e);
        }
    }
}
