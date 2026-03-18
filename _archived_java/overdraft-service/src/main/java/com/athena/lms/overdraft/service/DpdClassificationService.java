package com.athena.lms.overdraft.service;

import com.athena.lms.overdraft.entity.OverdraftBillingStatement;
import com.athena.lms.overdraft.entity.OverdraftFacility;
import com.athena.lms.overdraft.event.OverdraftEventPublisher;
import com.athena.lms.overdraft.repository.OverdraftBillingStatementRepository;
import com.athena.lms.overdraft.repository.OverdraftFacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DpdClassificationService {

    private final OverdraftFacilityRepository facilityRepo;
    private final OverdraftBillingStatementRepository billingRepo;
    private final OverdraftEventPublisher eventPublisher;
    private final AuditService auditService;

    /**
     * Daily DPD refresh — runs at 00:30 daily.
     * Updates DPD count and NPL stage for facilities with overdue billing statements.
     */
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void refreshDpd() {
        LocalDate today = LocalDate.now();
        log.info("Starting daily DPD refresh for {}", today);

        // Find all OPEN or OVERDUE statements past their due date
        List<OverdraftBillingStatement> overdueStatements = billingRepo
            .findByStatusIn(List.of("OPEN", "PARTIAL"));

        int updated = 0;
        for (OverdraftBillingStatement stmt : overdueStatements) {
            if (stmt.getDueDate().isBefore(today)) {
                // Mark as OVERDUE if still OPEN
                if ("OPEN".equals(stmt.getStatus())) {
                    stmt.setStatus("OVERDUE");
                    billingRepo.save(stmt);
                }

                try {
                    updateFacilityDpd(stmt, today);
                    updated++;
                } catch (Exception e) {
                    log.error("Failed to update DPD for facility {}: {}", stmt.getFacilityId(), e.getMessage());
                }
            }
        }
        log.info("DPD refresh complete: {} facilities updated for {}", updated, today);
    }

    private void updateFacilityDpd(OverdraftBillingStatement stmt, LocalDate today) {
        OverdraftFacility facility = facilityRepo.findById(stmt.getFacilityId()).orElse(null);
        if (facility == null) return;

        int dpd = (int) ChronoUnit.DAYS.between(stmt.getDueDate(), today);
        String previousStage = facility.getNplStage();
        String newStage = classifyStage(dpd);

        facility.setDpd(dpd);
        facility.setNplStage(newStage);
        facility.setLastDpdRefresh(today);
        facilityRepo.save(facility);

        eventPublisher.publishDpdUpdated(facility.getWalletId(), facility.getCustomerId(),
            dpd, newStage, facility.getTenantId());

        if (!previousStage.equals(newStage)) {
            eventPublisher.publishStageChanged(facility.getWalletId(), facility.getCustomerId(),
                previousStage, newStage, dpd, facility.getTenantId());

            auditService.audit(facility.getTenantId(), "FACILITY", facility.getId(), "STAGE_CHANGED",
                Map.of("previousStage", previousStage, "dpd", facility.getDpd()),
                Map.of("newStage", newStage, "dpd", dpd),
                Map.of("billingStatementId", stmt.getId().toString()));
        }

        log.info("Updated DPD for facility {} dpd={} stage={}", facility.getId(), dpd, newStage);
    }

    static String classifyStage(int dpd) {
        if (dpd <= 0) return "PERFORMING";
        if (dpd <= 30) return "WATCH";
        if (dpd <= 90) return "SUBSTANDARD";
        if (dpd <= 180) return "DOUBTFUL";
        return "LOSS";
    }
}
