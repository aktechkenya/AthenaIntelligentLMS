package com.athena.lms.overdraft.service;

import com.athena.lms.overdraft.entity.OverdraftBillingStatement;
import com.athena.lms.overdraft.entity.OverdraftFacility;
import com.athena.lms.overdraft.event.OverdraftEventPublisher;
import com.athena.lms.overdraft.repository.OverdraftBillingStatementRepository;
import com.athena.lms.overdraft.repository.OverdraftFacilityRepository;
import com.athena.lms.overdraft.repository.OverdraftFeeRepository;
import com.athena.lms.overdraft.repository.OverdraftInterestChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private static final BigDecimal MIN_PAYMENT_FLOOR = new BigDecimal("500");
    private static final BigDecimal MIN_PAYMENT_PCT = new BigDecimal("0.05");

    private final OverdraftFacilityRepository facilityRepo;
    private final OverdraftBillingStatementRepository billingRepo;
    private final OverdraftFeeRepository feeRepo;
    private final OverdraftInterestChargeRepository chargeRepo;
    private final OverdraftEventPublisher eventPublisher;
    private final AuditService auditService;

    /**
     * Monthly billing job — 1st of each month at 02:00.
     * Generates statements for active facilities with drawn balance > 0.
     */
    @Scheduled(cron = "0 0 2 1 * *")
    @Transactional
    public void generateMonthlyStatements() {
        LocalDate today = LocalDate.now();
        log.info("Starting monthly billing statement generation for {}", today);

        List<OverdraftFacility> facilities = facilityRepo
            .findByStatusAndDrawnAmountGreaterThan("ACTIVE", BigDecimal.ZERO);

        int generated = 0;
        for (OverdraftFacility facility : facilities) {
            try {
                if (billingRepo.existsByFacilityIdAndBillingDate(facility.getId(), today)) {
                    log.info("Billing statement already exists for facility {} on {}", facility.getId(), today);
                    continue;
                }
                generateStatement(facility, today);
                generated++;
            } catch (Exception e) {
                log.error("Failed to generate billing statement for facility {}: {}",
                    facility.getId(), e.getMessage());
            }
        }
        log.info("Monthly billing complete: {} statements generated for {}", generated, today);
    }

    private void generateStatement(OverdraftFacility facility, LocalDate billingDate) {
        LocalDate periodStart = billingDate.minusMonths(1);
        LocalDate periodEnd = billingDate.minusDays(1);

        BigDecimal closingBalance = facility.getDrawnAmount();
        BigDecimal interestAccrued = facility.getAccruedInterest();
        BigDecimal feesCharged = feeRepo.sumChargedFeesByFacilityId(facility.getId());

        // Calculate minimum payment: MAX(5% of closing balance, KES 500)
        BigDecimal fivePct = closingBalance.multiply(MIN_PAYMENT_PCT).setScale(4, RoundingMode.HALF_UP);
        BigDecimal minimumPayment = fivePct.max(MIN_PAYMENT_FLOOR).min(closingBalance);

        OverdraftBillingStatement stmt = new OverdraftBillingStatement();
        stmt.setTenantId(facility.getTenantId());
        stmt.setFacilityId(facility.getId());
        stmt.setBillingDate(billingDate);
        stmt.setPeriodStart(periodStart);
        stmt.setPeriodEnd(periodEnd);
        stmt.setOpeningBalance(closingBalance.subtract(interestAccrued));
        stmt.setInterestAccrued(interestAccrued);
        stmt.setFeesCharged(feesCharged);
        stmt.setPaymentsReceived(BigDecimal.ZERO);
        stmt.setClosingBalance(closingBalance);
        stmt.setMinimumPaymentDue(minimumPayment);
        stmt.setDueDate(billingDate.plusDays(30));
        stmt.setStatus("OPEN");
        billingRepo.save(stmt);

        // Update facility billing dates
        facility.setLastBillingDate(billingDate);
        facility.setNextBillingDate(billingDate.plusMonths(1));
        facilityRepo.save(facility);

        eventPublisher.publishBillingStatement(facility.getWalletId(), facility.getCustomerId(),
            closingBalance, minimumPayment, stmt.getDueDate(), facility.getTenantId());

        auditService.audit(facility.getTenantId(), "FACILITY", facility.getId(), "BILLING_GENERATED",
            null, Map.of("statementId", stmt.getId().toString(), "closingBalance", closingBalance,
                "minimumPayment", minimumPayment, "dueDate", stmt.getDueDate().toString()), null);

        log.info("Generated billing statement for facility {} closing={} minPayment={} due={}",
            facility.getId(), closingBalance, minimumPayment, stmt.getDueDate());
    }
}
