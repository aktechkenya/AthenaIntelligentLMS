package com.athena.lms.reporting.scheduler;

import com.athena.lms.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SnapshotScheduler {

    private final ReportingService reportingService;

    @Scheduled(cron = "0 30 1 * * *")
    public void generateDailySnapshots() {
        log.info("SnapshotScheduler: starting daily snapshot generation");
        try {
            reportingService.generateDailySnapshot("default");
            log.info("SnapshotScheduler: daily snapshot generation complete");
        } catch (Exception e) {
            log.error("SnapshotScheduler: error generating daily snapshot: {}", e.getMessage(), e);
        }
    }
}
